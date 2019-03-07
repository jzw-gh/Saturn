package com.vip.saturn.job.executor;

import org.quartz.Job;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entrance of Saturn executor.
 *
 * @author xiaopeng.he
 *
 */
public class Main {

	private String namespace;
	private String executorName;
	private String saturnLibDir = getLibDir("lib");
	private String appLibDir = getLibDir("lib");
	private ClassLoader executorClassLoader;
	private List<ClassLoader> jobClassLoaders;
	private Object saturnExecutor;
	private boolean executorClassLoaderShouldBeClosed;
	private boolean jobClassLoaderShouldBeClosed;

	protected void parseArgs(String[] inArgs) throws Exception {
		String[] args = inArgs.clone();

		for (int i = 0; i < args.length; i++) {
			String param = args[i].trim();
			switch (param) {
				case "-namespace":
					this.namespace = obtainParam(args, ++i, "namespace");
					System.setProperty("app.instance.name", this.namespace);
					System.setProperty("namespace", this.namespace);
					break;
				case "-executorName":
					this.executorName = obtainParam(args, ++i, "executorName");
					break;
				case "-saturnLibDir":
					this.saturnLibDir = obtainParam(args, ++i, "saturnLibDir");
					break;
				case "-appLibDir":
					this.appLibDir = obtainParam(args, ++i, "appLibDir");
					break;
				default:
					break;
			}
		}

		validateMandatoryParameters();
	}

	private String obtainParam(String[] args, int position, String paramName) {
		String value = null;
		if (position < args.length) {
			value = args[position].trim();
		}
		if (isBlank(value)) {
			throw new RuntimeException(String.format("Please set the value of parameter:%s", paramName));
		}
		return value;
	}

	private void validateMandatoryParameters() {
		if (isBlank(namespace)) {
			throw new RuntimeException("Please set the namespace parameter");
		}
	}

	private boolean isBlank(String str) {
		return str == null || str.trim().isEmpty();
	}

	public String getExecutorName() {
		if (saturnExecutor != null) {
			try {
				return (String) saturnExecutor.getClass().getMethod("getExecutorName").invoke(saturnExecutor);
			} catch (Exception e) {// NOSONAR
			}
		}
		return executorName;
	}

	private static String getLibDir(String dirName) {
		File root = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getParentFile();
		File lib = new File(root, dirName);
		if (!lib.isDirectory()) {
			return null;
		}
		return lib.getAbsolutePath();
	}

	private static List<URL> getUrls(File file) throws MalformedURLException {
		List<URL> urls = new ArrayList<>();
		if (!file.exists()) {
			return urls;
		}
		if (file.isDirectory()) {
			if ("classes".equals(file.getName())) {
				urls.add(file.toURI().toURL());
				return urls;
			}
			File[] files = file.listFiles();
			if (files != null && files.length > 0) {
				for (File tmp : files) {
					urls.addAll(getUrls(tmp));
				}
			}
			return urls;
		}
		if (file.isFile()) {
			urls.add(file.toURI().toURL());
		}
		return urls;
	}

	private void initClassLoader(ClassLoader executorClassLoader, List<ClassLoader> jobClassLoaders) throws Exception {
		setExecutorClassLoader(executorClassLoader);
		setJobClassLoader(jobClassLoaders);
	}

	private void setJobClassLoader(List<ClassLoader> jobClassLoaders) throws MalformedURLException {
		if (jobClassLoaders == null) {
			jobClassLoaders = new ArrayList<>();
			File appLibDirFile = new File(appLibDir);
			if (appLibDirFile.isDirectory()) {
				File[] subFiles = appLibDirFile.listFiles();
				if(subFiles != null){
					for(File file:subFiles){
						if(file.isDirectory()){
							List<URL> urls = getUrls(file);
							jobClassLoaders.add(new JobClassLoader(urls.toArray(new URL[urls.size()])));
							this.jobClassLoaderShouldBeClosed = true;
						}else {
							jobClassLoaders.add(this.executorClassLoader);
							this.jobClassLoaderShouldBeClosed = false;
						}
					}
				}
			}
			this.jobClassLoaders = jobClassLoaders;
		} else {
			this.jobClassLoaders = jobClassLoaders;
			this.jobClassLoaderShouldBeClosed = false;
		}
	}

	private void setExecutorClassLoader(ClassLoader executorClassLoader) throws MalformedURLException {
		if (executorClassLoader == null) {
			List<URL> urls = getUrls(new File(saturnLibDir));
			this.executorClassLoader = new SaturnClassLoader(urls.toArray(new URL[urls.size()]),
					Main.class.getClassLoader());
			this.executorClassLoaderShouldBeClosed = true;
		} else {
			this.executorClassLoader = executorClassLoader;
			this.executorClassLoaderShouldBeClosed = false;
		}
	}

	private void startExecutor(Map<ClassLoader,Object> saturnApplications) throws Exception {
		ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(executorClassLoader);
		try {
			Class<?> startExecutorClass = getSaturnExecutorClass();
			saturnExecutor = startExecutorClass
					.getMethod("buildExecutor", String.class, String.class, ClassLoader.class, List.class,
							Map.class)
					.invoke(null, namespace, executorName, executorClassLoader, jobClassLoaders, saturnApplications);
			startExecutorClass.getMethod("execute").invoke(saturnExecutor);
		} finally {
			Thread.currentThread().setContextClassLoader(oldCL);
		}
	}

	public void launch(String[] args, List<ClassLoader> jobClassLoaders) throws Exception {
		parseArgs(args);
		initClassLoader(null, jobClassLoaders);
		startExecutor(null);
	}

	public void launch(String[] args, List<ClassLoader> jobClassLoaders, Map<ClassLoader,Object> saturnApplications) throws Exception {
		parseArgs(args);
		initClassLoader(null, jobClassLoaders);
		startExecutor(saturnApplications);
	}

	public void launchInner(String[] args, ClassLoader executorClassLoader, List<ClassLoader> jobClassLoaders)
			throws Exception {
		parseArgs(args);
		initClassLoader(executorClassLoader, jobClassLoaders);
		startExecutor(null);
	}

	public void shutdown() throws Exception {
		shutdown("shutdown");
	}

	public void shutdownGracefully() throws Exception {
		shutdown("shutdownGracefully");
	}

	private void shutdown(String methodName) throws Exception {
		ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(executorClassLoader);
		try {
			Class<?> startExecutorClass = getSaturnExecutorClass();
			startExecutorClass.getMethod(methodName).invoke(saturnExecutor);
		} finally {
			Thread.currentThread().setContextClassLoader(oldCL);
			closeClassLoader();
		}
	}

	private Class<?> getSaturnExecutorClass() throws ClassNotFoundException {
		return executorClassLoader.loadClass("com.vip.saturn.job.executor.SaturnExecutor");
	}

	private void closeClassLoader() {
		try {
			if (jobClassLoaderShouldBeClosed && jobClassLoaders != null) {
				for(ClassLoader cl:jobClassLoaders){
					if(cl instanceof Closeable){
						((Closeable) cl).close();
					}
				}
			}
		} catch (IOException e) { // NOSONAR
		}
		try {
			if (executorClassLoaderShouldBeClosed && executorClassLoader != null
					&& executorClassLoader instanceof Closeable) {
				((Closeable) executorClassLoader).close();
			}
		} catch (IOException e) { // NOSONAR
		}
	}

	public static void main(String[] args) {
		System.out.println(args[0]);
		try {
			Main main = new Main();
			main.parseArgs(args);
			main.initClassLoader(null, null);
			main.startExecutor(null);
		} catch (InvocationTargetException ite) {// NOSONAR
			printThrowableAndExit(ite.getCause());
		} catch (Throwable t) {// NOSONAR
			printThrowableAndExit(t);
		}
	}

	private static void printThrowableAndExit(Throwable t) {
		if (t != null) {
			t.printStackTrace(); // NOSONAR
		}
		System.exit(1);
	}

	public Object getSaturnExecutor() {
		return saturnExecutor;
	}

	public void setExecutorName(String executorName) {
		this.executorName = executorName;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}
}