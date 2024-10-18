package edu.uic.cs.jmvx.runtime;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Consumer;

public class RRPrinter {

    public static void main(String[] args) throws Throwable {

        File f = new File(args[0]);
        boolean summary = args.length > 1 && args[1].equals("-s");
        boolean classFile = args.length > 1 && args[1].equals("-c");

        if (summary) {
            Summarizer s = new Summarizer();
            if (f.isFile())
                processFile(f, s);

            if (f.isDirectory())
                Arrays.stream(f.listFiles()).forEach(ff -> processFile(ff, s));

            LinkedList<Map.Entry<String, Long>> lst = new LinkedList<>();
            s.summary.entrySet().stream().forEach(lst::add);
            Collections.sort(lst, ((e1,e2) -> e1.getValue().compareTo(e2.getValue())));
            double total = lst.stream().map(Map.Entry::getValue).reduce(0L, Long::sum);
            lst.stream().forEach(e -> System.out.println("" + e + "\t\t" + ((double)e.getValue())/total));
        } else if(classFile){
            ClassInspector i = new ClassInspector();
            if (f.isFile())
                processFile(f, i);

            if (f.isDirectory())
                Arrays.stream(f.listFiles()).forEach(ff -> processFile(ff, i));
        } else {
            Consumer<Object> p = new Printer();
            if (f.isFile())
                processFile(f, p);

            if (f.isDirectory())
                Arrays.stream(f.listFiles()).forEach(ff -> processFile(ff, p));
        }
    }

    private static void processFile(File f, Consumer<Object> fun) {
        try {
            FileInputStream fis = new FileInputStream(f);

            if (fis.available() == 0)
                return;

            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(fis));

            if (!(f.getName().contains("classes") || f.getName().contains("clinits") || f.getName().contains("manifests")))
                ois.readInt();

            while (true) { //fis.available() > 0) {
                Object o = ois.readObject();
                fun.accept(o);
            }
        } catch (EOFException e) { //nop
        } catch (IOException | ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    private static void processStream(BufferedInputStream bis, Consumer<Object> fun) {
        try {
            if (bis.available() == 0)
                return;

            ObjectInputStream ois = new ObjectInputStream(bis);

            //if (!f.getName().endsWith("classes.dat"))
            //ois.readInt();

            try {
                ois.readObject(); //get rid of the null
                while (true) {
                    Object o = ois.readObject();
                    fun.accept(o);
                }
            }catch (IOException e){}
        } catch (IOException | ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    private static class Printer implements Consumer<Object> {
        @Override
        public void accept(Object o) {
            if(o == null)
                System.out.println("null");
            else
                System.out.println(o.getClass().getSimpleName());
        }
    }

    private static class ClassloaderPrinter implements Consumer<Object> {
        @Override
        public void accept(Object o) {
            if (o instanceof String) {
                String s = (String) o;
                if (s.endsWith("ClassLoader"))
                    System.out.println(s);
                else
                    System.out.println("\t" + s);
            }

            if (o instanceof byte[]) {
                ByteArrayInputStream bais = new ByteArrayInputStream((byte[])o);
                try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                    ois.readObject(); // Read the first null

                    System.out.println("\t\t" + ((byte[]) o).length);

                    while (bais.available() > 4) {
                        Object oo = ois.readObject();
                        System.out.println("\t\t" + oo.getClass().getSimpleName());
                    }
                } catch (IOException | ClassNotFoundException e) {
                    throw new Error(e);
                }
            }
        }
    }

    private static class Summarizer implements Consumer<Object> {
        private HashMap<String, Long> summary = new HashMap<>();
        @Override
        public void accept(Object o) {
            String className = o.getClass().getName();

            long count = summary.getOrDefault(className, 0L);
            summary.put(className, count+1);
        }
    }

    private static class ClassInspector implements Consumer<Object> {
        private static final Summarizer summarizer = new Summarizer();
        private static final Printer printer = new Printer();

        @Override
        public void accept(Object o) {
            if(o instanceof String){
                System.out.println("> " + o);
            }else if(o instanceof byte[]){
                processStream(new BufferedInputStream(new ByteArrayInputStream((byte[]) o)), printer);
                /*//try to recursively apply the RR printer
                //if o is a byte[] of data, this fails, hence the try/catch
                try {
                    processStream(new BufferedInputStream(new ByteArrayInputStream((byte[]) o)), printer);
                }catch (Exception e){
                    //o is just a byte array
                    printer.accept(o);
                }*/
            }else{
                printer.accept(o);
                //throw new Error("Illegal object of type " + o.getClass().getSimpleName());
            }
        }
    }
}
