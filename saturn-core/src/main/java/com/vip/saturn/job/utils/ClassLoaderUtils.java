package com.vip.saturn.job.utils;

import java.util.List;

public class ClassLoaderUtils {


    public static ClassLoader findClassLoader(Object classInstance,List<ClassLoader> classLoaders){
        String className = classInstance.getClass().getCanonicalName();
        return findClassLoader(className,classLoaders);
    }


    public static ClassLoader findClassLoader(String className, List<ClassLoader> classLoaders){

        if(classLoaders == null){
            return null;
        }

        for(ClassLoader cl:classLoaders){
            try{
                cl.loadClass(className);
                return cl;
            }catch (ClassNotFoundException ignored){

            }
        }

        return null;
    }
}
