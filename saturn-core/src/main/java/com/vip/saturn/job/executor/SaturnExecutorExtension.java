package com.vip.saturn.job.executor;

import java.util.List;
import java.util.Map;

/**
 *
 * @author hebelala
 *
 */
public abstract class SaturnExecutorExtension {

	protected String executorName;
	protected String namespace;
	protected List<ClassLoader> jobClassLoaders;
	protected ClassLoader executorClassLoader;

	public SaturnExecutorExtension(String executorName, String namespace, ClassLoader executorClassLoader,
			List<ClassLoader> jobClassLoaders) {
		this.executorName = executorName;
		this.namespace = namespace;
		this.jobClassLoaders = jobClassLoaders;
		this.executorClassLoader = executorClassLoader;
	}

	public abstract void initBefore();

	public abstract void initLogDirEnv();

	public abstract void initLog();

	public abstract void initAfter();

	public abstract void registerJobType();

	public abstract void validateNamespaceExisting(String connectString) throws Exception;

	public abstract void init();

	public abstract Class getExecutorConfigClass();

	public abstract void postDiscover(Map<String, String> discoveryInfo);

	public abstract void handleExecutorStartError(Throwable t);

}
