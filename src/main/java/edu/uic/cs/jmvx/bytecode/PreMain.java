package edu.uic.cs.jmvx.bytecode;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;

import edu.uic.cs.jmvx.runtime.JMVXRuntime;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.SerialVersionUIDAdder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.StupidCheckClassAdapterHack;
import org.objectweb.asm.util.TraceClassVisitor;


public class PreMain implements Opcodes{

	private static final class HackyClassWriter extends ClassWriter {

		private HackyClassWriter(ClassReader classReader, int flags) {
			super(classReader, flags);
		}

		protected String getCommonSuperClass(String type1, String type2) {
			return "java/lang/Object";
		}
	}

	public static class JMVXTransformer implements ClassFileTransformer {
		@Override
		public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		    // Information gathered during class analysis
			ClassNode cn;

			// First pass, is the class well formed to start with?
			if (!PreMain.verify(classfileBuffer, false)) {
				// No, don't touch it
				return classfileBuffer;
			}

			// Second pass, analyze the class being transformed and gather any information needed
			{
				ClassReader cr = new ClassReader(classfileBuffer);
				cn = new ClassNode();
				cr.accept(cn, 0);
			}

			byte[] ret = null;

			// Third pass, actually transform the class
			Optional<ClassLoader> optLoader = Optional.ofNullable(loader);
			try {
				ClassReader cr = new ClassReader(classfileBuffer);
				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

				ClassVisitor cv = cw;

				// Add UID for serialization
				int doNotInsertUID = ACC_ABSTRACT | ACC_INTERFACE;
				if ((cn.access & doNotInsertUID) == 0)
					cv = new SerialVersionUIDAdder(cw);

				// Add more transformers under this line as: cv = new ...
				cv = new InputStreamClassVisitor(optLoader, cv);
				cv = new OutputStreamClassVisitor(optLoader, cv);
				cv = new SocketInputStreamClassVisitor(optLoader, cv);
				cv = new SocketOutputStreamClassVisitor(optLoader, cv);
				if (JMVXRuntime.sync){
					cv = new MonitorClassVisitor(optLoader, cv);
					cv = new MakeSynchronizedClassVisitor(cv);
					cv = new ThreadPoolExecutorClassVisitor(cv);
				}
				cv = new FileOutputStreamClassVisitor(optLoader, cv);
				cv = new FileInputStreamClassVisitor(optLoader, cv);
				cv = new FileClassVisitor(optLoader, cv);
				cv = new FilesClassVisitor(cv);
				cv = new FileDescriptorClassVisitor(optLoader, cv);
				cv = new RandomAccessFileClassVisitor(optLoader, cv);
				cv = new ReplaceTypeClassVisitor(optLoader, cv);
				cv = new SocketClassVisitor(optLoader, cv);
				cv = new ServerSocketClassVisitor(optLoader, cv);
				cv = new ThreadClassVisitor(loader, cv);
				cv = new SystemClassVisitor(loader, cv);
				cv = new ClassloaderClassVisitor(optLoader, cv);
				cv = new ByteCodeVersionBumpClassVisitor(cv);
				cv = new RemoveJarCacheClassVisitor(cv);
				cv = new ResourceBundleControlClassVisitor(cv);
				//cv = new ReflectionClassVisitor(cv);
				cv = new FileChannelClassVisitor(optLoader, cv);
				cv = new ProcessClassVisitor(optLoader, cv);
				cv = new FileSystemProviderClassVisitor(optLoader, cv);
				cv = new SecureClassLoaderClassVisitor(optLoader, cv);
				cv = new JarFileCacheClassVisitor(cv);
				if(JMVXRuntime.logNatives)
					cv = new NativeMethodIntercepter(cn, cv);
				//these change the access flags on classes and natives
				//so they have to be after nativeMethodInterceptor
				cv = new UnixNativeDispatcherClassVisitor(cv);
				cv = new UnixFileAttributesClassVisitor(cv);
				cv = new UnixPathClassVisitor(cv);
				cv = new UnixExceptionClassVisitor(cv);
				cv = new FileDispatcherImplClassVisitor(optLoader, cv);
				cv = new TimeZoneClassVisitor(cv);
				cv = new ZipFileClassVisitor(cv);
				cv = new JarFileClassVisitor(cv);

				cr.accept(cv, 0);

				ret = cw.toByteArray();
			} catch (UnsupportedClassVersionError e) {
				throw e;
			} catch (Throwable t) {
				t.printStackTrace();

				try {
					File f = new File("z.class");
					if(f.getParentFile() != null)
						f.getParentFile().mkdirs();
					f.delete();
					try (FileOutputStream fos = new FileOutputStream(f)) {
						fos.write(classfileBuffer);
					}
					try(PrintWriter fw = new PrintWriter("lastClass.txt")) {
						fw.println(cn.name);
					}
				} catch (Throwable t2) {
					t2.printStackTrace();
				}
				return null;
			}

			// Verify instrumented class
			PreMain.verify(ret, true);

			//THROW TRY FINALLY RELATED STUFF HERE
			//there is a bug wrt try/finally in verify
			ClassReader cr2 = new ClassReader(ret);
			ClassWriter cw2 = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			ClassVisitor cv2 = new ThreadRunClassVisitor(loader, cw2);
			cv2 = new InterceptMainMethodClassVisitor(cv2);
			cv2 = new clinitClassVisitor(optLoader, cv2);
			if (JMVXRuntime.sync)
				cv2 = new SynchronizedClassVisitor(loader, cv2);

			cr2.accept(cv2, 0);

			ret = cw2.toByteArray();

			return ret;
		}

		public byte[] transforma(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
			ClassReader cr = new ClassReader(classfileBuffer);
			className = cr.getClassName();
			if (isIgnoredClass(cr.getClassName()))
				return null;
			if (DEBUG)
				System.out.println("Inst: " + cr.getClassName());

			boolean skipFrames = false;
			ClassNode cn = new ClassNode();
			cr.accept(cn, ClassReader.SKIP_CODE);
			if (cn.version >= 100 || cn.version <= 50 || className.endsWith("$Access4JacksonSerializer") || className.endsWith("$Access4JacksonDeSerializer"))
				skipFrames = true;

			if (skipFrames) {
				// This class is old enough to not guarantee frames.
				// Generate new frames for analysis reasons, then make sure
				// to not emit ANY frames.
				ClassWriter cw = new HackyClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
				cr.accept(new ClassVisitor(Opcodes.ASM7, cw) {
					@Override
					public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
						return new JSRInlinerAdapter(super.visitMethod(access, name, desc, signature, exceptions), access, name, desc, signature, exceptions);
					}
				}, 0);
				cr = new ClassReader(cw.toByteArray());
			}

			try {
				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
				// Luis:  New class visitors go here
				ClassVisitor cv = cw;

				cr.accept(cv, ClassReader.EXPAND_FRAMES);
				if (DEBUG) {
					File f = new File("debug-record/" + className + ".class");
					f.getParentFile().mkdirs();
					FileOutputStream fos = new FileOutputStream(f);
					fos.write(cw.toByteArray());
					fos.close();
				}
				try {
					cr = new ClassReader(cw.toByteArray());
					CheckClassAdapter ca = new CheckClassAdapter(new ClassWriter(0));
					cr.accept(ca, 0);
				} catch (ArrayIndexOutOfBoundsException e) {
					// This may be due to bugs inside CheckClassAdapter
					// Ignore, the CheckClassAdapter rejects classes *NOT* by throwing errors
				}
				return cw.toByteArray();
			} catch (Throwable t) {
				t.printStackTrace();

				TraceClassVisitor tcv = null;
				PrintWriter fw = null;
				try {
					File f = new File("z.class");
					if(f.getParentFile() != null)
						f.getParentFile().mkdirs();
					f.delete();
					FileOutputStream fos = new FileOutputStream(f);
					fos.write(classfileBuffer);
					fos.close();
					fw = new PrintWriter("lastClass.txt");
				} catch (Throwable t2) {
					t2.printStackTrace();
				} finally {
					if(tcv != null)
						tcv.visitEnd();
					fw.close();
				}

				return null;
			}
		}
	}

	public static boolean isIgnoredClass(String className) {
		if (whiteList != null) {
			for (String s : whiteList)
				if (className.startsWith(s))
					return false;
			return true;
		}
		return /*className.startsWith("java") || className.startsWith("com/sun") || className.startsWith("sun/") || */ className.startsWith("edu/uic/cs/jmvx") || className.startsWith("com/rits/cloning") || className.startsWith("jdk")
				|| className.startsWith("com/thoughtworks") || className.startsWith("org/xmlpull") || className.startsWith("org/kxml2");
	}

	static boolean DEBUG = false;
	static String[] whiteList;

	public static void premain(String _args, Instrumentation inst) {
		if (_args != null) {
			String[] args = _args.split(",");
			for (String arg : args) {
				String[] d = arg.split("=");
				if (d[0].equals("debug")) {
					DEBUG = true;
				} else if (d[0].equals("whitelist")) {
					whiteList = d[1].split(";");
				}
			}
		}
		ClassFileTransformer transformer = new JMVXTransformer();
		inst.addTransformer(transformer);

	}

	public static void main(String[] args) throws Throwable {
		DEBUG = true;
		FileInputStream fis = new FileInputStream("z.class");
		byte[] b = new byte[1024 * 1024 * 2];
		int l = fis.read(b);
		fis.close();
		byte[] a = new byte[l];
		System.arraycopy(b, 0, a, 0, l);
		new JMVXTransformer().transform(null, null, null, null, a);
	}

	// Mostly copy-pasted from ASM
	public static boolean verify(byte[] classBytes, boolean stopOnError) {
		ClassNode classNode = new ClassNode();
		ClassReader classReader = new ClassReader(classBytes);
		classReader.accept(
				new CheckClassAdapter(Opcodes.ASM7, classNode, false) {}, ClassReader.SKIP_DEBUG);

		Type syperType = classNode.superName == null ? null : Type.getObjectType(classNode.superName);
		List<MethodNode> methods = classNode.methods;

		List<Type> interfaces = new ArrayList<>();
		for (String interfaceName : classNode.interfaces) {
			interfaces.add(Type.getObjectType(interfaceName));
		}

		for (MethodNode method : methods) {
			SimpleVerifier verifier =
					new SimpleVerifier(
							Type.getObjectType(classNode.name),
							syperType,
							interfaces,
							(classNode.access & Opcodes.ACC_INTERFACE) != 0);
			Analyzer<BasicValue> analyzer = new Analyzer<>(verifier);
//				if (loader != null) {
//					verifier.setClassLoader(loader);
//				}
			try {
				analyzer.analyze(classNode.name, method);
			} catch (AnalyzerException e) {
				if (e.getCause().getClass().equals(TypeNotPresentException.class)
						&& e.getCause().getCause().getClass().equals(ClassNotFoundException.class)
				) { /* Missing class, skip error  */ }
				else {
					if(stopOnError) {
						StupidCheckClassAdapterHack.printAnalyzerResult(method, analyzer, new PrintWriter(System.err, true));
						throw new Error(e);
					} else {
						return false;
					}
				}
			} catch (IllegalAccessError e) {
				if(stopOnError) {
					StupidCheckClassAdapterHack.printAnalyzerResult(method, analyzer, new PrintWriter(System.err, true));
					throw new Error(e);
				} else {
					return false;
				}
			}
		}
		return true;
	}

}
