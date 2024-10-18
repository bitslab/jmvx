package edu.uic.cs.jmvx;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import edu.uic.cs.jmvx.runtime.JMVXNativeDetector;

/**
 * Class to help analyze stack traces exported from strace assisted system call analysis.
 * This program will filter stack traces based on the top <code>depth</code> elements
 * And dump them to stdout.
 * The rest of the analysis (e.g., finding good wrappers) is up to you to do by hand
 */
public class SysCallAnalyzer2 {

    public static ObjectInputStream in;
    public static ConcurrentHashMap<StackTraceElement, List<JMVXNativeDetector.Trace>> found;
    public static int depth;
    public static HashSet<String> ignore = new HashSet<>();
    public static boolean checkNotWrapped = false;

    static {
        ignore.add("doPrivileged");
        ignore.add("forName0");
        ignore.add("invoke0");
        ignore.add("newInstance0");
    }

    /**
     * Prints a table of functions we've logged
     * Function on top of stack -> count of unqiue stack traces (after filtering)
     *
     * @throws Exception
     */
    public static void printTable() throws Exception {
        found.forEach((k, v) -> {
            System.out.format("%s -> %d\n", k, v.size());
        });
        System.out.println(found.size());
    }

    /**
     * Creates a StackTraceElement from a string. Note, this function
     * doesn't handle all cases, just basic ones of the form
     * className.methodName(fileName)
     * It will not parse file numbers or native method strings
     *
     * @param s
     * @return
     */
    public static StackTraceElement TraceElementFromString(String s) {
        int openParen = s.indexOf('(');
        int closeParen = s.indexOf(')');
        int lastDot = s.lastIndexOf('.', openParen);
        String clazz = s.substring(0, lastDot);
        String mthd = s.substring(lastDot + 1, openParen);
        String file = s.substring(openParen + 1, closeParen);
        return new StackTraceElement(clazz, mthd, file, -1);
    }

    /**
     * Prints a stack trace up to <param>depth</param> frames
     *
     * @param trace
     * @param maxDepth
     */
    public static void printStack(JMVXNativeDetector.Trace trace, int maxDepth) {
	StackTraceElement[] stack = trace.stack;
        int n = Math.min(maxDepth, stack.length);
        for (int i = 0; i < n; i++) {
            System.out.println("\t" + stack[i]);
        }
    }

    /**
     * Print unique stacks for a given method call we've logged
     *
     * @param func
     * @throws Exception
     */
    public static void printSpecific(String func) throws Exception {
        StackTraceElement key = TraceElementFromString(func);
        try {
            for (JMVXNativeDetector.Trace trace : found.get(key)) {
                System.out.println("---------");
                printStack(trace, trace.stack.length); //depth
            }
        } catch (NullPointerException e) {
        }
    }

    /**
     * Print all filtered stack traces for methods we've logged
     *
     * @throws Exception
     */
    public static void dumpAll() throws Exception {
        for (Map.Entry<StackTraceElement, List<JMVXNativeDetector.Trace>> e : found.entrySet()) {
            System.out.format("%s -> %d\n", e.getKey(), e.getValue().size());
            for (JMVXNativeDetector.Trace stack : e.getValue()) {
                System.out.println("---------");
                printStack(stack, depth);
            }
        }
        System.out.println(found.size());
    }

    /**
     * Filters the stack traces stored in the map
     * This can leave some keeps bound to an empty list,
     * meaning the method was logged but either is protected by some other
     * call to JMVX or a method we don't care about (e.g. invoke0)
     *
     * @param depth
     * @return
     * @throws Exception
     */
    public static ConcurrentHashMap<StackTraceElement, List<JMVXNativeDetector.Trace>> filterMap(int depth) throws Exception {
        /* Note for the future, can maybe compose filtering and analysis.
         * That way, we only iterate over the map once */
        //found = (ConcurrentHashMap<StackTraceElement, List<StackTraceElement[]>>) in.readObject();
        //hack here with the type change. The list is actully a LinkedList
        //List<E> doesn't just have a remove first or last, so we need to cast to LinkedList to get the op
        //needed by filterList
        found.replaceAll((k, v) -> filterList(depth, (LinkedList<JMVXNativeDetector.Trace>) v));
        return found;
    }

    /**
     * Removes duplicate stacks in a list of traces.
     * Duplicate is determined by comparing <param>depth</param> frames in the stack trace
     *
     * @param depth
     * @param stacks
     * @return
     */
    public static List<JMVXNativeDetector.Trace> filterList(int depth, LinkedList<JMVXNativeDetector.Trace> stacks) {
        LinkedList<JMVXNativeDetector.Trace> filtered = new LinkedList<>();
        JMVXNativeDetector.Trace trace;
        while ((trace = stacks.pollFirst()) != null) {
            //hack is lambda safe
            final StackTraceElement[] hack = trace.stack;
            /* remove duplicate/similar stacks.
             * then we have to see if the native is handled
             * SingleLeaderWithoutCoordinator.foo would eliminate all functions
             * called after it (in theory anyway)
             */
            if (!stacks.stream().anyMatch(s -> similarStack(hack, s.stack, depth))
            ){//&& notProtected(stack))
		if(checkNotWrapped){
                    if(notWrapped(trace.stack))
                        filtered.add(trace);//filterStack(stack));
                }else
                    filtered.add(trace);
            }
        }
        return filtered;

    }

	/*public static String filterOut[] = {
			"edu.uic.cs.jmvx"
	};*/

    /**
     * Remove functions we don't care about from a stack trace
     * E.g., removes edu.uic.cs.jmvx function calls from the stack trace
     *
     * @param stack
     * @return
     */
    public static StackTraceElement[] filterStack(StackTraceElement[] stack) {
        return Arrays.stream(stack)
                .filter(s -> !s.getClassName().startsWith("edu.uic.cs.jmvx")).toArray(StackTraceElement[]::new);
    }
    
     /**
     * Checks if a native call happened after a JMVX calls
     *
     * @param stack
     * @return
     */
    public static boolean notWrapped(StackTraceElement[] stack) {
        return Arrays.stream(stack)
                .noneMatch(s -> s.getClassName().startsWith("edu.uic.cs.jmvx.runtime.strategy"));
    }

    /**
     * Checks if two stack frames are similar up to depth (or the size of the smallest
     * stack if one is smaller than depth)
     *
     * @param a
     * @param b
     * @param depth
     * @return
     */
    public static boolean similarStack(StackTraceElement[] a, StackTraceElement[] b, int depth) {
        int n = Math.min(Math.min(a.length, b.length), depth);
        boolean same = false;
        for (int i = 0; i < n; i++) {
            same = a[i].equals(b[i]);
            if (!same)
                return false;
        }
        return true;
    }

    public static boolean notProtected(StackTraceElement[] stack) {
        int singleLeaderAt = -1;
        int nativeAt = -1;
        for (int i = 0; i < stack.length; i++) {
            if (stack[i].isNativeMethod() && !ignore.contains(stack[i].getMethodName()))
                nativeAt = i;
                //not shadowed by a jmvx instrumented method or class loading
            else if (stack[i].getClassName().startsWith("edu.uic.cs.jmvx.runtime.strategy"))
                singleLeaderAt = i;
        }
        //no call into SingleLeader or a native is before it
        return singleLeaderAt < 0 || singleLeaderAt < nativeAt;
    }

    public static void mergeMapIntoFound(ConcurrentHashMap<StackTraceElement, List<JMVXNativeDetector.Trace>> a) {
        //for all data in map a, add new key, value pair to found
        //or append value to list if the key is already mapped.
        a.forEach((k, v) -> found.merge(k, v, (key, val) -> {
            val.addAll(v);
            return val;
        }));
    }

    public static boolean likelyAnonClass(StackTraceElement frame) {
        //probably should've just used regex
        String name = frame.getClassName();
        int lastDollar = name.lastIndexOf('$');
        if (lastDollar == -1)
            return false;
        try {
            Integer.parseInt(name.substring(lastDollar + 1));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static StackTraceElement simpleWrapper(JMVXNativeDetector.Trace trace) {
        for (StackTraceElement frame : trace.stack) {
            if (!(frame.getClassName().startsWith("edu.uic.cs") ||
                    frame.getMethodName().startsWith("$JMVX$") ||
                    frame.getMethodName().startsWith("access$") ||
                    frame.isNativeMethod() ||
                    likelyAnonClass(frame))) {
                return frame;
            }
        }
        return null;
    }

    public static void simpleList() {
        Set<StackTraceElement> wrappers = new HashSet<>();
        found.forEach((k, v) -> {
            v.forEach((stack) -> {
                StackTraceElement wrap = simpleWrapper(stack);
                if (wrap != null)
                    wrappers.add(wrap);
            });
        });

        wrappers.forEach(System.out::println);
    }

    public static void printHelp() {
        System.out.println("Usage:");
        System.out.println("java -cp $JMVX_JAR edu.uic.cs.jmvx.runtime.SysCallAnalyzer [--depth <int>] mode file(s)");
        System.out.println("--depth <n> -- filters by the top n frames of each stack");
        System.out.println("mode - ");
        System.out.println("\ttable - print out logged methods that lead to system calls and number of stack traces after filtering");
        System.out.println("\tdump - same as table, but prints the unique stacks (up to depth) as well");
        System.out.println("\tsimple - prints a list of methods to instrument to cover the observed system calls");
        System.out.println("\tspecific <stack element> - prints out the stacks of a particular stack trace element");
    }

    public static void main(String args[]) throws Exception{
        if(args.length == 0){
            printHelp();
            return;
        }

        String mode = null;
        String specific = null;

        depth = Integer.MAX_VALUE;

        int argi = 0;
        SETTINGS_LOOP: for(; argi < args.length; argi++){
            String argv = args[argi];
            switch (argv){
                case "--help":
                case "-h":
                    printHelp();
                    return;
                case "--depth":
                    depth = Integer.parseInt(args[++argi]);
                    break;
                case "--notWrapped":
                    checkNotWrapped = true;
                    break;
                case "specific":
                    mode = argv;
                    specific = args[++argi];
                    break SETTINGS_LOOP;
                case "table":
                case "dump":
                case "simple":
                    mode = argv;
                    break SETTINGS_LOOP;
                default:
                    System.err.println("Unrecognized argument: " + argv);
            }
        }


        //merge maps in different files
        //(or maps in the same file)
        found = new ConcurrentHashMap<>();
        for(argi++; argi < args.length; argi++){ //for(String filename: args) {
            String filename = args[argi];
            try {
                in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(filename)));
                while (true) {
                    ConcurrentHashMap<StackTraceElement, List<JMVXNativeDetector.Trace>> nd =
                            (ConcurrentHashMap<StackTraceElement, List<JMVXNativeDetector.Trace>>) in.readObject();
                    mergeMapIntoFound(nd);
                }
            }catch (EOFException e){
		if(in != null)
                    in.close();
            } //pass
        }

        //in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(args[0])));
        //found = (ConcurrentHashMap<StackTraceElement, List<StackTraceElement[]>>) in.readObject();
        found = filterMap(depth);

        switch (mode){
            case "dump":
                dumpAll();
                break;
            case "specific":
                printSpecific(specific);
                break;
            case "simple":
                simpleList();
                break;
            default:
                printTable();
        }
    }
}
