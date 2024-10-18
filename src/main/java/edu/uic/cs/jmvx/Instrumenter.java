package edu.uic.cs.jmvx;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import edu.uic.cs.jmvx.bytecode.PreMain;
import org.apache.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class Instrumenter implements Opcodes {
    private static final boolean PROCESS_CODE_IN_ZIPS = (System.getProperty("JMVX_PROCESS_CODE_IN_ZIPS") != null);

    public static ClassLoader loader;

    private static Logger logger = Logger.getLogger(Instrumenter.class);

    private static final int NUM_PASSES = 2;

    private static final int PASS_ANALYZE = 0;

    private static final int PASS_OUTPUT = 1;

    public static final boolean IS_DACAPO = false;

    private static volatile int pass_number = 1;

    ForkJoinPool es = new ForkJoinPool();

    public static final boolean FINALIZERS_ENABLED = true;

    public static final String FIELD_LOGICAL_CLOCK = "_chronicler_clock";

    private static File rootOutputDir;

    private String lastInstrumentedClass = "";

    private static String generatedFolderName = "generated";


    private static HashSet<String> DENY_LIST = new HashSet<>();

    static{
        /**
	 * See inidividual methods for notes on what classes they
	 * deny and why
         */
        denyPMDBenchmarkClasses();
	denyCaches();
	denyWeakCoupling();
	denyJVMThreadClasses();
    }

    private static void denyJVMThreadClasses(){
	/*
	 * Denies instrumenting Finalizers, Reference handles, and shutdown hooks
	 * We don't interfere with resource management at this level.
	 */	    
	DENY_LIST.add("java/lang/ref/Reference$ReferenceHandler");
	DENY_LIST.add("java/lang/ref/Finalizer$FinalizerThread");
	DENY_LIST.add("java/lang/ApplicationShutdownHooks$1");
    }

    private static void denyWeakCoupling(){
    	/*these classes have methods whose function is relient on weak memory
    	or have methods that are called varying number of times throughout the benchmark
	called a variable number of times.
	
	org/sunflow/core/Geometry - 
	the synchronized methods are initializers. The <i>intersect<\i> method
	uses double check locking. This class will always run in an arbitrary
	order, but the order doesn't really matter, because the methods are
	initializers.  <b>Sunflow will typically deadlock if this class is
	instrumented</b> 
	
	org/sunflow/system/UI - 
	called inside methods that use double check lock, e.g.,
	org.sunflow.core.Geometry which is also denied here.  Therefore, the
	number of times this method is called is unpredictable.  Luckily, this
	class doesn't actually do anything in the benchmark--by default, the UI
	is turned off nor does it print anything to the command line. Tracking
	the monitor for this class just adds overhead.  

        org/sunflow/system/Timer - 
	Methods (generate System.nanoTime events) called inside a block
	"guarded" by a double check lock, so the thread that executes the call
	is unpredictable.

	org/apache/xml/dtm/ref/DTMManagerDefault -
	IteratorPool and ObjectPool
	Coupled to weak memory...? This was added to a deny list without a note
	Coming back to it, Xalan doesn't work without it being denied.
	There are various double checked locks used in Xalan, maybe there is one here


	org/h2/message/TraceSystem - 
	Has a member that is an LRU cache that interacts with this classes
	monitor. Thus, this class can affect the vector clock
	
	org/h2/util/MemoryUtils - 
	This is a garbage collector implemented inside of java
	volatile memory working with locks.
	* */
	DENY_LIST.add("org/sunflow/core/Geometry");
	DENY_LIST.add("org/sunflow/system/UI");
	DENY_LIST.add("org/sunflow/system/Timer");
	DENY_LIST.add("org/apache/xml/dtm/ref/DTMManagerDefault");
	DENY_LIST.add("org/apache/xpath/axes/IteratorPool");
	DENY_LIST.add("org/apache/xml/utils/ObjectPool");
	//DENY_LIST.add("org/h2/message/TraceSystem");
	//DENY_LIST.add("org/h2/util/MemoryUtils");
    }


    private static void denyCaches(){
	/*
	*Reasons for skipping classes:
	org/python/core/PyType and org/python/core/PyJavaType - 
	put and get data from a CustomConcurrentHashMap that uses weak entries
	which can be GC'ed at anytime. If the JVM's differ on GC, then the
	programs diverge because these methods will take different routes and
	generate different sequences of monitorenter/exit's.
	
	org/apache/fop/fo/properties/PropertyCache - 
	cache that uses a mix of weak memory and locks the weak mem affects how
	many times a lock is grabbed and creates benign divergences
	*/
	DENY_LIST.add("org/python/core/PyType");
	DENY_LIST.add("org/python/core/PyJavaType");
    	DENY_LIST.add("org/apache/fop/fo/properties/PropertyCache");
    }
    private static void denyPMDBenchmarkClasses(){
    	/* pmd does bytecode analysis on itself, particularly on these classes
	 * Instrumenting them actively changes the performance of the benchmark
	 * Skip instrumenting them so we have comparable performance to vanilla
     	*/ 
        DENY_LIST.add("net/sourceforge/pmd/ast/ASTMethodDeclaration");
        DENY_LIST.add("net/sourceforge/pmd/ast/JavaParser");
        DENY_LIST.add("net/sourceforge/pmd/ast/JavaParserTokenManager");
        DENY_LIST.add("net/sourceforge/pmd/ast/TokenMgrError");
        DENY_LIST.add("net/sourceforge/pmd/cpd/AnyTokenizer");
        DENY_LIST.add("net/sourceforge/pmd/cpd/MatchAlgorithm");
        DENY_LIST.add("net/sourceforge/pmd/cpd/cppast/CPPParserTokenManager");
        DENY_LIST.add("net/sourceforge/pmd/cpd/cppast/TokenMgrError");
        DENY_LIST.add("net/sourceforge/pmd/dcd/ClassLoaderUtil");
        DENY_LIST.add("net/sourceforge/pmd/dcd/UsageNodeVisitor");
        DENY_LIST.add("net/sourceforge/pmd/dcd/graph/ConstructorNode");
        DENY_LIST.add("net/sourceforge/pmd/dcd/graph/FieldNode");
        DENY_LIST.add("net/sourceforge/pmd/dcd/graph/MethodNode");
        DENY_LIST.add("net/sourceforge/pmd/dcd/graph/UsageGraphBuilder");
        DENY_LIST.add("net/sourceforge/pmd/AbstractRuleChainVisitor");
        DENY_LIST.add("net/sourceforge/pmd/ant/Formatter");
        DENY_LIST.add("net/sourceforge/pmd/ast/ASTAnnotation");
        DENY_LIST.add("net/sourceforge/pmd/ast/ASTLiteral");
        DENY_LIST.add("net/sourceforge/pmd/cpd/LanguageFactory");
        DENY_LIST.add("net/sourceforge/pmd/cpd/Match");
        DENY_LIST.add("net/sourceforge/pmd/cpd/SourceFileOrDirectoryFilter");
        DENY_LIST.add("net/sourceforge/pmd/cpd/TokenEntry");
        DENY_LIST.add("net/sourceforge/pmd/cpd/cppast/SimpleCharStream");
        DENY_LIST.add("net/sourceforge/pmd/dcd/graph/ClassNodeComparator");
        DENY_LIST.add("net/sourceforge/pmd/dcd/graph/MemberNodeComparator");
        DENY_LIST.add("net/sourceforge/pmd/dfa/DaaRule");
        DENY_LIST.add("net/sourceforge/pmd/cpd/CPD");
        DENY_LIST.add("net/sourceforge/pmd/cpd/GUI");
        DENY_LIST.add("net/sourceforge/pmd/dcd/DCD");
    }

    private static void finishedPass() {
        switch (pass_number) {
            case PASS_ANALYZE:
                break;
            case PASS_OUTPUT:
                break;
        }
    }

	public static PreMain.JMVXTransformer transformer = new PreMain.JMVXTransformer();

    private ClassNode cn;

    private void analyzeClass(InputStream inputStream) {
    }

    private AtomicInteger count = new AtomicInteger(0);

    private byte[] instrumentClass(InputStream is, ClassLoader cl) {
        try {
			ClassReader cr = new ClassReader(is);
			ClassWriter cw = new ClassWriter(cr, 0);
			cr.accept(cw, 0);
			byte[] b = cw.toByteArray();
			lastInstrumentedClass = cr.getClassName();
			if(DENY_LIST.contains(lastInstrumentedClass)){
				return b;
			}
			b =  transformer.transform(cl, null, null, null, b);
			if(b==null)
			{
				System.err.println("on " + lastInstrumentedClass);
                System.err.println("skipping");
//				System.exit(-1);
			}

			return b;
        } catch (Exception ex) {
            logger.error("Exception processing class: " + lastInstrumentedClass, ex);
            return null;
        } finally {
            int i = count.getAndIncrement();
            if ((i % 1_000) == 0)
                System.out.println("\t" + i + " classes instrumented");
        }
    }

    public void _main(String inputFolder, String outputFolder, String[] classpathEntries) {

        rootOutputDir = new File(outputFolder);
        if (!rootOutputDir.exists())
            rootOutputDir.mkdir();

        // Setup the class loader
        URL[] urls = new URL[classpathEntries.length];
        for (int i = 0 ; i < classpathEntries.length; i++) {
            File f = new File(classpathEntries[i]);
            if (!f.exists()) {
                System.err.println("Unable to read path " + classpathEntries[i]);
                System.exit(-1);
            }
            if (f.isDirectory() && !f.getAbsolutePath().endsWith("/"))
                f = new File(f.getAbsolutePath() + "/");
            try {
                urls[i] = f.getCanonicalFile().toURI().toURL();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        loader = new URLClassLoader(urls, Instrumenter.class.getClassLoader());

        for (pass_number = 0; pass_number < NUM_PASSES; pass_number++) // Do
                                                                       // each
                                                                       // pass.
        {
            ConcurrentLinkedQueue<Future> futures = new ConcurrentLinkedQueue<>();

            File f = new File(inputFolder);
            if (!f.exists()) {
                System.err.println("Unable to read path " + inputFolder);
                System.exit(-1);
            }
            if (f.isDirectory())
                processDirectory(f, rootOutputDir, true, futures);
            else if (inputFolder.endsWith(".jar"))
                processJar(f, rootOutputDir, null);
            else if (inputFolder.endsWith(".zip"))
                processZip(f, rootOutputDir);
            else if (inputFolder.endsWith(".class"))
                try {
                    processClass(f.getName(), new FileInputStream(f), rootOutputDir);
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            else {
                System.err.println("Unknown type for path " + inputFolder);
                System.exit(-1);
            }

            Future ff;
            while ((ff = futures.poll()) != null) {
                while (!ff.isDone()) {
                    try {
                        ff.get();
                    } catch (InterruptedException | ExecutionException e) {
                        continue;
                    }
                }
            }

            finishedPass();
        }

//        this.generateInputStreams();

    }

//    private void generateInputStreams() {
//        File generatedDir = Paths.get(rootOutputDir.getAbsolutePath(), generatedFolderName).toFile();
//        generatedDir.mkdirs();
//
//        for (Type t : transformer.classesToGenerate) {
//            try {
//                InputStreamGenerator isg = new InputStreamGenerator();
//                byte[] bytes = isg.generateInputStreamClass(t);
//
//                PreMain.verify(bytes, true);
//
//                File newFile = isg.generateEmptyFile(generatedDir);
//                newFile.getParentFile().mkdirs();
//                try (FileOutputStream fos = new FileOutputStream(newFile)) {
//                    fos.write(bytes);
//                }
//            } catch (ClassNotFoundException e) {
//                logger.debug("Could not generate InputStream for class " + t.getClassName());
//            } catch (IOException e) {
//                throw new Error(e);
//            }
//        }
//    }

    private void processClass(String name, InputStream is, File outputDir) {
        switch (pass_number) {
            case PASS_ANALYZE:
                analyzeClass(is);
                break;
            case PASS_OUTPUT:
                try {
                    FileOutputStream fos = new FileOutputStream(outputDir.getPath()
                            + File.separator + name);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    lastInstrumentedClass = outputDir.getPath() + File.separator + name;
                    bos.write(instrumentClass(is, null));
                    bos.writeTo(fos);
                    fos.close();

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
        }
    }

    private void processDirectory(File f, File parentOutputDir, boolean isFirstLevel, ConcurrentLinkedQueue<Future> futures) {
        File thisOutputDir;
        if (isFirstLevel) {
            thisOutputDir = parentOutputDir;
        } else {
            thisOutputDir = new File(parentOutputDir.getAbsolutePath() + File.separator
                    + f.getName());
            if (pass_number == PASS_OUTPUT)
                thisOutputDir.mkdir();
        }
        for (File fi : f.listFiles()) {
            if (fi.isDirectory())
                processDirectory(fi, thisOutputDir, false, futures);
            else if (fi.getName().endsWith(".class"))
                try {
                    processClass(fi.getName(), new FileInputStream(fi), thisOutputDir);
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            else if (fi.getName().endsWith(".jar")) {
                if (!thisOutputDir.exists())
                    thisOutputDir.mkdir();
                Future ff = es.submit(() -> {
                    processJar(fi, thisOutputDir, null);
                });
                futures.add(ff);
            } else if (fi.getName().endsWith(".zip")) {
                processZip(fi, thisOutputDir);
            } else if (pass_number == PASS_OUTPUT) {
                File dest = new File(thisOutputDir.getPath() + File.separator + fi.getName());
                FileChannel source = null;
                FileChannel destination = null;

                try {
                    source = new FileInputStream(fi).getChannel();
                    destination = new FileOutputStream(dest).getChannel();
                    destination.transferFrom(source, 0, source.size());
                } catch (Exception ex) {
                    logger.error("Unable to copy file " + fi, ex);
                    System.exit(-1);
                } finally {
                    if (source != null) {
                        try {
                            source.close();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                    if (destination != null) {
                        try {
                            destination.close();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }

            }
        }

    }

    private void processJar(File src, File outputDir, ClassLoader cl) {
        try {

            // @SuppressWarnings("resource")
            JarFile jar = new JarFile(src);
            JarOutputStream jos = null;
            if (pass_number == PASS_OUTPUT)
                // jos = new JarOutputStream(os);
                jos = new JarOutputStream(new FileOutputStream(outputDir.getPath() + File.separator + src.getName()));
            Enumeration<JarEntry> entries = jar.entries();

            ConcurrentLinkedQueue<Future> futures = new ConcurrentLinkedQueue<>();

            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                switch (pass_number) {
                    case PASS_ANALYZE:
                        if (e.getName().endsWith(".class")) {
                            try (InputStream is = jar.getInputStream(e)) {
                                analyzeClass(is);
                            }
                        }
                        break;
                    case PASS_OUTPUT:
                        if (e.getName().endsWith(".class") /*&& !e.getName().startsWith("java")*/
                                && !e.getName().equals("module-info.class")
                                && !e.getName().startsWith("org/objenesis")
                                && !e.getName().startsWith("com/thoughtworks/xstream/")
                                && !e.getName().startsWith("com/rits/cloning")
                                && !e.getName().startsWith("com/apple/java/Application")
                                && !e.getName().startsWith("net/sf/cglib/")) {
                            {
                                JarOutputStream privateJos = jos;
                                Future<byte[]> f1 = es.submit((Callable<byte[]>) () -> {
                                    try (InputStream is = jar.getInputStream(e)) {
                                        ClassLoader loader = new URLClassLoader(new URL[]{src.toURI().toURL()}, cl);
                                        byte[] clazz = instrumentClass(is, loader);
                                        return clazz;
                                    } catch (UnsupportedClassVersionError | SecurityException ex) {
                                        logger.error("Unable to process file " + src.getName(), ex);
                                        return null;
                                    } catch (IOException ex) {
                                        logger.error("Unable to process jar", ex);
                                        System.exit(-1);
                                        return null;
                                    }
                                });
                                Future f2 = es.submit(() -> {
                                    JarEntry outEntry = new JarEntry(e.getName());
                                    synchronized (privateJos) {
                                        try {
                                            byte[] clazz = f1.get();
                                            privateJos.putNextEntry(outEntry);
                                            if (clazz == null) {
                                                logger.error("Failed to instrument " + e.getName());
                                                try (InputStream is2 = jar.getInputStream(e)) {
                                                    byte[] buffer = new byte[1024];
                                                    while (true) {
                                                        int count = is2.read(buffer);
                                                        if (count == -1)
                                                            break;
                                                        privateJos.write(buffer, 0, count);
                                                    }
                                                }
                                            } else {
                                                privateJos.write(clazz);
                                                privateJos.closeEntry();
                                            }
                                        } catch (IOException | InterruptedException | ExecutionException ex) {
                                            logger.error("Unable to process jar", ex);
                                            System.exit(-1);
                                        }
                                    }

                                });
                                futures.add(f1);
                                futures.add(f2);
                            }

                        } else {
                            JarEntry outEntry = new JarEntry(e.getName());
                            if (e.isDirectory()) {
                                synchronized (jos) {
                                    jos.putNextEntry(outEntry);
                                    jos.closeEntry();
                                }
                            } else if (e.getName().startsWith("META-INF")
                                    && (e.getName().endsWith(".SF") || e.getName().endsWith(".RSA"))) {
                                // don't copy this
                            } else if (e.getName().equals("META-INF/MANIFEST.MF")) {
                                Scanner s = new Scanner(jar.getInputStream(e));

                                synchronized (jos) {
                                    jos.putNextEntry(outEntry);

                                    String curPair = "";
                                    while (s.hasNextLine()) {
                                        String line = s.nextLine();
                                        if (line.equals("")) {
                                            curPair += "\n";
                                            if (!curPair.contains("SHA1-Digest:"))
                                                jos.write(curPair.getBytes());
                                            curPair = "";
                                        } else {
                                            curPair += line + "\n";
                                        }
                                    }
                                    s.close();
                                    jos.write("\n".getBytes());
                                    jos.closeEntry();
                                }
                            } else {
                                synchronized (jos) {
                                    jos.putNextEntry(outEntry);
                                    InputStream is = jar.getInputStream(e);
                                    byte[] buffer = new byte[1024];
                                    while (true) {
                                        int count = is.read(buffer);
                                        if (count == -1)
                                            break;
                                        jos.write(buffer, 0, count);
                                    }
                                    jos.closeEntry();
                                }
                            }
                        }
                }

            }

            Future f;
            while ((f = futures.poll()) != null)
                f.get();

            if (jos != null) {
                jos.close();

            }
            jar.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            logger.error("Unable to process jar", e);
            System.exit(-1);
        }

    }

    private void processZip(File f, File outputDir) {
        try {
            // @SuppressWarnings("resource")
            ZipFile zip = new ZipFile(f);
            ZipOutputStream zos = null;
            if (pass_number == PASS_OUTPUT)
                zos = new ZipOutputStream(new FileOutputStream(outputDir.getPath() + File.separator
                        + f.getName()));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                switch (pass_number) {
                    case PASS_ANALYZE:
                        if (PROCESS_CODE_IN_ZIPS && e.getName().endsWith(".class")) {
                            try (InputStream is = zip.getInputStream(e)) {
                                analyzeClass(is);
                            }
                        } else if (PROCESS_CODE_IN_ZIPS && e.getName().endsWith(".jar")) {
                            File tmp = new File("/tmp/classfile");
                            if (tmp.exists())
                                tmp.delete();
                            FileOutputStream fos = new FileOutputStream(tmp);
                            byte buf[] = new byte[1024];
                            int len;
                            InputStream is = zip.getInputStream(e);
                            while ((len = is.read(buf)) > 0) {
                                fos.write(buf, 0, len);
                            }
                            is.close();
                            fos.close();

                            processJar(tmp, new File("/tmp"), null);
                            // processJar(jar.getInputStream(e), jos);
                        }
                        break;
                    case PASS_OUTPUT:
                        if (PROCESS_CODE_IN_ZIPS
                                && e.getName().endsWith(".class") && !e.getName().startsWith("java")
                                && !e.getName().startsWith("org/objenesis")
                                && !e.getName().startsWith("com/thoughtworks/xstream/")
                                && !e.getName().startsWith("com/rits/cloning")
                                && !e.getName().startsWith("com/apple/java/Application")) {
                            {
                                ZipEntry outEntry = new ZipEntry(e.getName());
                                zos.putNextEntry(outEntry);
                                try (InputStream is = zip.getInputStream(e)) {
                                    byte[] clazz = instrumentClass(is, null);
                                    if (clazz == null) {
                                        try (InputStream is2 = zip.getInputStream(e)) {
                                            byte[] buffer = new byte[1024];
                                            while (true) {
                                                int count = is2.read(buffer);
                                                if (count == -1)
                                                    break;
                                                zos.write(buffer, 0, count);
                                            }
                                        }
                                    } else
                                        zos.write(clazz);
                                    zos.closeEntry();
                                }
                            }

                        } else if (PROCESS_CODE_IN_ZIPS && e.getName().endsWith(".jar")) {
                            ZipEntry outEntry = new ZipEntry(e.getName());
                            // jos.putNextEntry(outEntry);
                            // try {
                            // processJar(jar.getInputStream(e), jos);
                            // jos.closeEntry();
                            // } catch (FileNotFoundException e1) {
                            // // TODO Auto-generated catch block
                            // e1.printStackTrace();
                            // }

                            File tmpSrc = File.createTempFile("jmvx", ".jar");
                            File tmpDest = Files.createTempDirectory("jmvx").toFile();
                            try {
                                try (FileOutputStream fos = new FileOutputStream(tmpSrc)) {
                                    try (InputStream is = zip.getInputStream(e)) {
                                        byte[] buffer = new byte[1024];
                                        while (true) {
                                            int count = is.read(buffer);
                                            if (count == -1)
                                                break;
                                            fos.write(buffer, 0, count);
                                        }
                                    }
                                }
                                // System.out.println("Done reading");
                                ClassLoader cl = new URLClassLoader(new URL[]{ tmpSrc.toURI().toURL() }, Instrumenter.class.getClassLoader());
                                processJar(tmpSrc, tmpDest, cl);

                                zos.putNextEntry(outEntry);
                                try (FileInputStream is = new FileInputStream(new File(tmpDest, tmpSrc.getName()))) {
                                    byte[] buffer = new byte[1024];
                                    while (true) {
                                        int count = is.read(buffer);
                                        if (count == -1)
                                            break;
                                        zos.write(buffer, 0, count);
                                    }
                                }
                                zos.closeEntry();
                            } catch(IOException ex) {
                                throw new Error(ex);
                            } finally {
                                tmpSrc.delete();
                                new File(tmpDest, tmpSrc.getName()).delete();
                                tmpDest.delete();
                            }
                        } else {
                            ZipEntry outEntry = new ZipEntry(e.getName());
                            if (e.isDirectory()) {
                                zos.putNextEntry(outEntry);
                                zos.closeEntry();
                            } else if (e.getName().startsWith("META-INF")
                                    && (e.getName().endsWith(".SF") || e.getName().endsWith(".RSA"))) {
                                // don't copy this
                            } else if (e.getName().equals("META-INF/MANIFEST.MF")) {
                                Scanner s = new Scanner(zip.getInputStream(e));
                                zos.putNextEntry(outEntry);

                                String curPair = "";
                                while (s.hasNextLine()) {
                                    String line = s.nextLine();
                                    if (line.equals("")) {
                                        curPair += "\n";
                                        if (!curPair.contains("SHA1-Digest:"))
                                            zos.write(curPair.getBytes());
                                        curPair = "";
                                    } else {
                                        curPair += line + "\n";
                                    }
                                }
                                s.close();
                                zos.write("\n".getBytes());
                                zos.closeEntry();
                            } else {
                                zos.putNextEntry(outEntry);
                                InputStream is = zip.getInputStream(e);
                                byte[] buffer = new byte[1024];
                                while (true) {
                                    int count = is.read(buffer);
                                    if (count == -1)
                                        break;
                                    zos.write(buffer, 0, count);
                                }
                                zos.closeEntry();
                            }
                        }
                }

            }
            if (pass_number == PASS_OUTPUT) {
                zos.close();
                zip.close();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            logger.error("Unable to process zip" + f, e);
            System.exit(-1);
        }

    }
}
