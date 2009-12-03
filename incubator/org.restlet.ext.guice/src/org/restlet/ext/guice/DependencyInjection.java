package org.restlet.ext.guice;

import java.lang.annotation.Annotation;

import org.restlet.resource.Finder;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;
import org.restlet.routing.TemplateRoute;

/**
 * Dependency-injecting versions of Finder, {@link Router#attach}, {@link Router#attachDefault},
 * {@link Filter#setNext}, {@link Server#setNext}, and {@link ServerList#setNext}.
 */
public interface DependencyInjection {

    /**
     * Returns a Finder that will obtain an instance of the ServerResource
     * subtype bound to the type associated with the given class.
     */
    Finder finderFor(Class<? extends ServerResource> cls);

    /**
     * Returns a Finder that will obtain an instance of the ServerResource
     * subtype bound to the type and qualifier associated with the given class.
     */
    Finder finderFor(Class<? extends ServerResource> cls, Class<? extends Annotation> qualifier);

    /**
     * Calls {@link Router#attach router.attach} with the result of calling
     * {@link DependencyInjection#finderFor finderFor(targetClass)}.
     */
    TemplateRoute attach(Router router, String pathTemplate, Class<? extends ServerResource> targetClass);

    /**
     * Calls {@link Router#attach router.attach} with the result of calling
     * {@link DependencyInjection#finderFor finderFor(targetClass, qualifier)}.
     */
    TemplateRoute attach(Router router, String pathTemplate, Class<? extends ServerResource> targetClass, Class<? extends Annotation> qualifier);

    /**
     * Calls {@link Router#attach router.attachDefault} with the result of calling
     * {@link DependencyInjection#finderFor finderFor(targetClass)}.
     */
    TemplateRoute attachDefault(Router router, Class<? extends ServerResource> targetClass);

    /**
     * Calls {@link Router#attach router.attachDefault} with the result of calling
     * {@link DependencyInjection#finderFor finderFor(targetClass, qualifier)}.
     */
    TemplateRoute attachDefault(Router router, Class<? extends ServerResource> targetClass, Class<? extends Annotation> qualifier);

    /**
     * Calls {@link Filter#setNext target.setNext} with the result of calling
     * {@link DependencyInjection#finderFor finderFor(targetClass)}.
     * The target object must be a {@link Filter}, {@link Server}, or {@link ServerList}.
     */
    void setNext(Object target, Class<? extends ServerResource> nextClass);

    /**
     * Calls {@link Filter#setNext target.setNext} with the result of calling
     * {@link DependencyInjection#finderFor finderFor(targetClass, qualifier)}.
     * The target object must be a {@link Filter}, {@link Server}, or {@link ServerList}.
     */
    void setNext(Object target, Class<? extends ServerResource> nextClass, Class<? extends Annotation> qualifier);
}
