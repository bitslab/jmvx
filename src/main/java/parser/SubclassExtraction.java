package parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.VoidType;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


public class SubclassExtraction {

    private static final Path directory = Paths.get("src/main/java/parser/output/");
    private static String classPackage = "package parser.output;\n";
    private static Set<String> deny = new HashSet<>();

    static { // Set of methods to ignore
            deny.add("main");
    }
    public static void main(String[] args) throws IOException {
        createDirectory();
        for(String arg : args){
            Path argPath = Paths.get(arg);
            String className = argPath.getFileName().toString().replaceAll("\\.java$", "");
            CompilationUnit cu = StaticJavaParser.parse(Files.newInputStream(argPath)); // Create tree
            Optional<ClassOrInterfaceDeclaration> parent; // Class or Interface 'node'

            if(cu.getClassByName(className).isPresent()) { // Check whether it's a class or interface
                throw new Error("Classes not implemented");
            }
            else if(cu.getInterfaceByName(className).isPresent()) {
                parent = cu.getInterfaceByName(className);
            }else
                throw new Error("Neither interface or class");

            ClassOrInterfaceDeclaration classes = interfaceToClass(parent.get());

            List<?> imports = cu.getImports(); // Get imports and declarations

            Path path = Paths.get(String.format("%s/%sParsed.java", directory, className));

            try( BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))){
                writer.write(classPackage);
                for(Object imp : imports){
                    writer.write(imp.toString());
                }
                writer.write(classes.toString());
            }catch (IOException e){
                System.err.println("Error writing file contents");
            }
        }
    }

    private static List<TypeDeclaration<?>> getSubclasses(Optional<ClassOrInterfaceDeclaration> parent){

        return parent.get().getMembers().stream() // Traverse AST and grab all subclasses of file
            .filter(member -> member instanceof ClassOrInterfaceDeclaration)
            .map(member -> (ClassOrInterfaceDeclaration) member)
            .collect(Collectors.toList());
    }


    private static ClassOrInterfaceDeclaration interfaceToClass(ClassOrInterfaceDeclaration parent){
        CompilationUnit result = new CompilationUnit();
        ClassOrInterfaceDeclaration mainClass = result.addClass(parent.getNameAsString());
        NodeList<Modifier> classMods = new NodeList<>(); // Modifiers for class 'public static'
        classMods.add(new Modifier(Modifier.Keyword.PUBLIC));
        classMods.add(new Modifier(Modifier.Keyword.STATIC));

        for(MethodDeclaration method : parent.getMethods()){
            if(deny.contains(method.getNameAsString()))
                continue;
            StringBuilder methodName = new StringBuilder(method.getNameAsString());
            NodeList<Parameter> parameters = method.getParameters();

            for(Parameter param : parameters){ // Creating class name
                if(param.getTypeAsString().contains("JMVX")){
                    String combined = param.getTypeAsString();
                    methodName.append(combined);
                    continue;
                }
                methodName.append(param.getTypeAsString().charAt(0));
            }

            ClassOrInterfaceDeclaration subclass = new ClassOrInterfaceDeclaration(); // Creating class
            subclass.setModifiers(classMods); // General class definitions
            subclass.addImplementedType("Serializable");
            subclass.setName(methodName.toString());

            for(Parameter param : parameters){ // Adding all fields to class
                if(param.getTypeAsString().contains("JMVX")){
                    continue;
                }
                subclass.addField(param.getType(), param.getNameAsString());
            }

            if(!method.getThrownExceptions().isEmpty()){ // Adding Exception
                // Only one exception is supported for now
                ReferenceType exception = method.getThrownException(0);
                subclass.addField(exception, "exception");
            }

            if(!(method.getType() instanceof VoidType)){ // Adding return type
                subclass.addField(method.getType(), "ret");
            }

            Random random = new Random(); // Add random long for serializable
            long randomValue = random.nextLong(); // Quirky hack to add the 'L' at the end
            FieldDeclaration randomLong = subclass.addFieldWithInitializer(PrimitiveType.longType(), "serialUID",
                    new IntegerLiteralExpr(randomValue + "L"), Modifier.Keyword.PRIVATE);
            mainClass.addMember(addConstructors(subclass, parameters, method));
        } // End of method iteration

        System.out.println(mainClass.toString());
        return mainClass;
    }

    private static void buildConstructor(ConstructorDeclaration constructor, NodeList<Parameter> parameters){
        BlockStmt b = new BlockStmt();
        for(Parameter param : parameters){
            if(param.getTypeAsString().contains("JMVX")){
                continue;
            }
            constructor.addParameter(param);
            AssignExpr a = new AssignExpr(
                    new NameExpr(String.format("this.%s", param.getNameAsString())), // target
                    new NameExpr(param.getNameAsString()),                           // value
                    AssignExpr.Operator.ASSIGN);
            b.addStatement(a);
        }
        constructor.setBody(b);
    }

    private static ClassOrInterfaceDeclaration addConstructors(ClassOrInterfaceDeclaration subclass,
                                                               NodeList<Parameter> parameters,
                                                               MethodDeclaration method){

        if(!parameters.isEmpty()){ // Regular constructor
            NodeList<Parameter> withoutJMVX = new NodeList<>();
            for(Parameter param : parameters){
                if(param.getTypeAsString().startsWith("JMVX"))
                    continue;
                withoutJMVX.add(param);
            }
            if(!withoutJMVX.isEmpty()){ // If the only parameter is of JMVX type, then skip
                ConstructorDeclaration base = subclass.addConstructor();
                buildConstructor(base, withoutJMVX);
            }
        }

        if(!(method.getType() instanceof VoidType)){ // Create return constructor
            ConstructorDeclaration ret = subclass.addConstructor();
            NodeList<Parameter> withReturn = new NodeList<>(parameters);
            withReturn.add(new Parameter(method.getType(), "ret"));
            buildConstructor(ret, withReturn);
        }

        if(!(method.getThrownExceptions().isEmpty())){ // Create exception constructor
            ConstructorDeclaration except = subclass.addConstructor();
            NodeList<Parameter> withException = new NodeList<>(parameters);
            withException.add(new Parameter(method.getThrownException(0).asReferenceType(), "exception"));
            buildConstructor(except, withException);
        }
        return subclass;
    }

    private static void createDirectory(){
        Path path = Paths.get(directory.toUri());

        if(!Files.exists(path)){
            try{
                Files.createDirectory(path);
            } catch (IOException e){
                System.err.printf("Error creating outputs dir: %s", e);
            }
        }
    }
}

