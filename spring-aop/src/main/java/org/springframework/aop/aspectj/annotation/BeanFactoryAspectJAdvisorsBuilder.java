/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	@Nullable
	private volatile List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * Look for AspectJ-annotated aspect beans in the current bean factory,
	 * and return to a list of Spring AOP Advisors representing them.
	 * <p>Creates a Spring Advisor for each AspectJ advice method.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors() {
		// 获取缓存中的aspectBeanNames
		List<String> aspectNames = this.aspectBeanNames;

		if (aspectNames == null) {
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();
					// 获取 beanFactory 中所有的 beanNames
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);
					for (String beanName : beanNames) {
						if (!isEligibleBean(beanName)) {
							continue;
						}
						// We must be careful not to instantiate beans eagerly as in this case they
						// would be cached by the Spring container but would not have been weaved.
						Class<?> beanType = this.beanFactory.getType(beanName, false);
						if (beanType == null) {
							continue;
						}
						// 找出所有类上面含 @Aspect 注解的beanName
						if (this.advisorFactory.isAspect(beanType)) {
							// 将找到的 beanName 放入 aspectNames 集合
							aspectNames.add(beanName);
							AspectMetadata amd = new AspectMetadata(beanType, beanName);
							if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
								MetadataAwareAspectInstanceFactory factory =
										new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
								// 1.找到切面类的所有但是不包括 @Pointcut 注解的方法
								// 2.筛选出来包含 @Around, @Before, @After,@ AfterReturning， @AfterThrowing 注解的方法
								// 3.封装为 List<Advisor> 返回
								List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
								if (this.beanFactory.isSingleton(beanName)) {
									// 将上面找出来的 Advisor 按照 key 为 beanName，value为 List<Advisor> 的形式存入 advisorsCache
									this.advisorsCache.put(beanName, classAdvisors);
								}
								else {
									this.aspectFactoryCache.put(beanName, factory);
								}
								advisors.addAll(classAdvisors);
							}
							else {
								// Per target or per this.
								if (this.beanFactory.isSingleton(beanName)) {
									throw new IllegalArgumentException("Bean with name '" + beanName +
											"' is a singleton, but aspect instantiation model is not singleton");
								}
								MetadataAwareAspectInstanceFactory factory =
										new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
								this.aspectFactoryCache.put(beanName, factory);
								advisors.addAll(this.advisorFactory.getAdvisors(factory));
							}
						}
					}
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}

		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}
		List<Advisor> advisors = new ArrayList<>();
		for (String aspectName : aspectNames) {
			// 当再次进入该方法，会直接从 advisorsCache 缓存中获取
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			}
			else {
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		return advisors;
	}

	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
