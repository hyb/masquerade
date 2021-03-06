package run;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import masquerade.sim.run.ServerRunner;

/**
 * Standalone Masquerade runner using an embedded Jetty to launch the simulator.
 * 
 * <p>Supported system properties:</p>
 * <ul>
 * <li>masquerade.port: TCP Port to bind HTTP server to (default: 8888)</li>
 * <li>masquerade.home: Directory under which masquerade files are stored</li>
 * <li>masquerade.requestlog.dir: Directory where request history payload is stored (default: ${masquerade.home}/requestlog)</li>
 * <li>masquerade.artifact.dir: Directory where uploaded templates, scripts, ... are stored (default: ${masquerade.home}/artifact)</li>
 * <li>masquerade.plugin.dir: Directory where plugins are are stored (default: ${masquerade.home}/plugins)</li>
 * </ul>
 */
public class Main {

	private int port;

	public Main(int port) {
		this.port = port;
	}

	/**
	 * Entry point. See class documentation for arguments.
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		int port = Integer.parseInt(System.getProperty("masquerade.port", "8888"));
		
		Main main = new Main(port);
		main.run();
	}

	/**
	 * Starts masquerade in an embedded Jetty container
	 * @return 
	 * @return 0 if succesful, -1 if unable to determine war location
	 * @throws Exception
	 */
	private boolean run() throws Exception {
		String warPath = getWarPath();
		if (warPath == null) {
			System.err.println("Unable to determine WAR file this application was loaded from");
			return false;
		}
		
		File unpackDir = unpack(warPath);
		if (unpackDir == null) {
			System.err.println("Unable to unpack WAR file to temporary folder");
			return false;
		}
		
		// Create class loader with class path for webapp (lib and classes)
		File libDir = new File(unpackDir, "WEB-INF/lib");
		File runLibDir = new File(unpackDir, "WEB-INF/librun");
		File bundleDir = new File(unpackDir, "WEB-INF/bundles");
		File classesDir = new File(unpackDir, "WEB-INF/classes");
		ClassLoader serverRunnerLoader = createClassLoader(libDir, runLibDir, bundleDir, classesDir);
		
		// Add shutdown hook
		Runtime.getRuntime().addShutdownHook(new DeleteTempDirShutdownHook(serverRunnerLoader, unpackDir));
		
		runServer(serverRunnerLoader, unpackDir);
		
		return true;
	}

	/**
	 * Runs the {@link ServerRunner}, loading it using reflection to avoid dependency from this class
	 * ServerRunner as the class loader for this class will be unable to load Jetty.
	 * @param serverRunnerLoader 
	 * @param unpackDir Where the WAR has been exploded to
	 */
	private void runServer(ClassLoader serverRunnerLoader, File unpackDir) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		Thread.currentThread().setContextClassLoader(serverRunnerLoader);
		Class<?> runner = serverRunnerLoader.loadClass("masquerade.sim.run.ServerRunner");
		Method runServer = runner.getMethod("runServer", File.class, int.class);
		System.out.println("Starting Masquerade Standalone on port " + port);
		runServer.invoke(null, unpackDir, port);
	}

	/**
	 * Creates a {@link ClassLoader} for loading Jetty, includes all JARs in 
	 * the webapp's lib dir plus Jetty bundles
	 */
	private ClassLoader createClassLoader(File libdir, File runLibDir, File bundledir, File classesDir) throws MalformedURLException {
		File[] libJarFiles = findJars(libdir);
		File[] runLibJarFiles = findJars(runLibDir);
		
		File[] jettyJarFiles = bundledir.listFiles(new FilenameFilter() {
			@Override public boolean accept(File dir, String name) {
				return name.matches("jetty.*\\.jar") || name.startsWith("commons-io");
			}
		});
		
		Collection<File> allJars = new ArrayList<File>();
		allJars.addAll(Arrays.asList(libJarFiles));
		allJars.addAll(Arrays.asList(runLibJarFiles));
		allJars.addAll(Arrays.asList(jettyJarFiles));
		
		URL[] urls = new URL[allJars.size() + 1];
		int i = 1;
		urls[0] = classesDir.toURI().toURL();
		for (File jar : allJars) {
			urls[i++] = jar.toURI().toURL();
		}
		return new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
	}

	/**
	 * @return Path to the WAR file this application was loaded from, or <code>null</code> if not found
	 * @throws UnsupportedEncodingException 
	 */
	private String getWarPath() throws UnsupportedEncodingException {
		CodeSource source = getClass().getProtectionDomain().getCodeSource();
		if (source != null) {
			URL location = source.getLocation();
			String encodedFile = location.getFile();
			String decodedFile = URLDecoder.decode(encodedFile, System.getProperty("file.encoding"));
			System.out.println("Starting: " + decodedFile);
			return isWar(decodedFile) ? decodedFile : null;
		} else {
			System.err.println("CodeSource for JAR is null");
			return null;
		}
	}

	private static File[] findJars(File libdir) {
		return libdir.listFiles(new FilenameFilter() {
			@Override public boolean accept(File dir, String name) {
				return name.endsWith(".jar");
			}
		});
	}

	private static boolean isWar(String path) {
		return path != null && new File(path).isFile();
	}

	/**
	 * Unpacks the WAR archive at the specified directory to a temporary directory
	 * @param warPath 
	 * @return The root webapp directory of the exploded WAR
	 * @throws IOException
	 */
	private static File unpack(String warPath) throws IOException {
		File dir = createTempDir();
		if (dir == null) {
			return null;
		}
		
		Zippy.unzip(warPath, dir);
		
		return dir;
	}

	/**
	 * Creates a temporary directory suitable to explode the WAR intos
	 * @return The temp. dirs
	 * @throws IOException
	 */
	private static File createTempDir() throws IOException {
		File temp = File.createTempFile("masq", null);
		temp.deleteOnExit();
		File tempDir = new File(temp.getParentFile(), temp.getName() + "-war");

		if (!tempDir.mkdir()) {
			System.err.println("Cannot create directory " + tempDir.getAbsolutePath());
			return null;
		}
	
		return tempDir;
	}
}
