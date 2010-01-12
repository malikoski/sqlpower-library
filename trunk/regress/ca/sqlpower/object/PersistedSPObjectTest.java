/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of SQL Power Library.
 *
 * SQL Power Library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SQL Power Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.object;

import java.awt.Image;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.log4j.Logger;

import ca.sqlpower.dao.PersistedSPOProperty;
import ca.sqlpower.dao.PersisterUtils;
import ca.sqlpower.dao.SPPersister;
import ca.sqlpower.dao.SPPersisterListener;
import ca.sqlpower.dao.helper.SPPersisterHelperFactory;
import ca.sqlpower.dao.helper.generated.SPPersisterHelperFactoryImpl;
import ca.sqlpower.dao.session.SessionPersisterSuperConverter;
import ca.sqlpower.object.annotation.Accessor;
import ca.sqlpower.object.annotation.Mutator;
import ca.sqlpower.object.annotation.NonBound;
import ca.sqlpower.object.annotation.NonProperty;
import ca.sqlpower.object.annotation.Transient;
import ca.sqlpower.sql.DataSourceCollection;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sqlobject.DatabaseConnectedTestCase;
import ca.sqlpower.sqlobject.SQLObjectRoot;
import ca.sqlpower.testutil.GenericNewValueMaker;
import ca.sqlpower.testutil.NewValueMaker;
import ca.sqlpower.testutil.SPObjectRoot;
import ca.sqlpower.testutil.StubDataSourceCollection;
import ca.sqlpower.util.SPSession;
import ca.sqlpower.util.SessionNotFoundException;
import ca.sqlpower.util.StubSPSession;

/**
 * Classes that implement SPObject and need to be persisted must implement
 * a test class that extends this test case.
 */
public abstract class PersistedSPObjectTest extends DatabaseConnectedTestCase {
	
	private static final Logger logger = Logger.getLogger(PersistedSPObjectTest.class);
	
	/**
	 * This workspace contains the root SPObject made in setup. This is only needed
	 * for connecting the root to a session in setup. If a formal root object
	 * for a session gets created in the library it can replace this stub version. 
	 */
	private class StubWorkspace extends AbstractSPObject {
		
		private final SPSession session;

		public StubWorkspace(SPSession session) {
			this.session = session;
		}

		@Override
		protected boolean removeChildImpl(SPObject child) {
			return false;
		}

		public boolean allowsChildren() {
			return true;
		}

		public int childPositionOffset(Class<? extends SPObject> childType) {
			return 0;
		}

		public List<Class<? extends SPObject>> getAllowedChildTypes() {
			ArrayList<Class<? extends SPObject>> list = new ArrayList<Class<? extends SPObject>>();
			list.add(SPObjectRoot.class);
			return list;
		}

		public List<? extends SPObject> getChildren() {
			return Collections.singletonList(root);
		}

		public List<? extends SPObject> getDependencies() {
			return Collections.emptyList();
		}

		public void removeDependency(SPObject dependency) {
			//do nothing
		}
		
		@Override
		public SPSession getSession() throws SessionNotFoundException {
			return session;
		}
		
	}
	
	private SPObjectRoot root;
	
	public PersistedSPObjectTest(String name) {
		super(name);
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		root = new SPObjectRoot();
		SPSession stub = new StubSPSession() {
			private final SPObject workspace = new StubWorkspace(this);
			@Override
			public SPObject getWorkspace() {
				return workspace;
			}
		};
		root.setParent(stub.getWorkspace());
		SQLObjectRoot sqlRoot = new SQLObjectRoot();
		root.addChild(sqlRoot, 0);
		sqlRoot.addDatabase(db, 0);
	}

	/**
	 * Returns an object of the type being tested. Will be used in reflective
	 * tests being done for persisting objects. This must be a descendant of the
	 * root object returned from {@link #getRootObject()}.
	 */
	public abstract SPObject getSPObjectUnderTest();
	
	public SPObject getRootObject() {
		return root;
	}

	/**
	 * Returns a persister helper factory that can properly persist an object of
	 * the type returned by {@link #getSPObjectUnderTest()}. This can be overridden
	 * if a different factory is required beyond the default one.
	 * 
	 * @param targetPersister
	 *            The persister that will have persist calls made on it when the
	 *            helper factory has methods called.
	 * @param converter The converter for the factory.
	 */
	public SPPersisterHelperFactory getPersisterHelperFactory(
			SPPersister targetPersister, SessionPersisterSuperConverter converter) {
		return new SPPersisterHelperFactoryImpl(targetPersister, converter);
	}
	
	/**
	 * Tests the SPPersisterListener will persist a property change to its
	 * target persister.
	 */
	public void testSPListenerPersistsProperty() throws Exception {
		NewValueMaker valueMaker = new GenericNewValueMaker(root);
		DataSourceCollection<SPDataSource> dsCollection = new StubDataSourceCollection<SPDataSource>();
		SessionPersisterSuperConverter converter = new SessionPersisterSuperConverter(
				dsCollection, root);
		CountingSPPersister countingPersister = new CountingSPPersister();
		SPPersisterListener listener = new SPPersisterListener(
				getPersisterHelperFactory(countingPersister, converter));
		
		SPObject spObject = getSPObjectUnderTest();
		spObject.addSPListener(listener);
		
		SPObject wo = getSPObjectUnderTest();
        wo.addSPListener(listener);

        List<PropertyDescriptor> settableProperties;
        settableProperties = Arrays.asList(PropertyUtils.getPropertyDescriptors(wo.getClass()));
        
        Set<String> propertiesToPersist = findPersistableBeanProperties(false);

        for (PropertyDescriptor property : settableProperties) {
            Object oldVal;
            
            if (!propertiesToPersist.contains(property.getName())) continue;

            countingPersister.clearAllPropertyChanges();
            try {
                oldVal = PropertyUtils.getSimpleProperty(wo, property.getName());

                // check for a setter
                if (property.getWriteMethod() == null) continue;
                
            } catch (NoSuchMethodException e) {
                logger.debug("Skipping non-settable property " + property.getName() + " on " +
                		wo.getClass().getName());
                continue;
            }
            
            Object newVal = valueMaker.makeNewValue(property.getPropertyType(), oldVal, property.getName());
            int oldChangeCount = countingPersister.getPersistPropertyCount();
            
            try {
                logger.debug("Setting property '" + property.getName() + "' to '" + newVal + 
                		"' (" + newVal.getClass().getName() + ")");
                BeanUtils.copyProperty(wo, property.getName(), newVal);

                assertTrue("Did not persist property " + property.getName(), 
                		oldChangeCount < countingPersister.getPersistPropertyCount());
                
                //The first property change at current is always the property change we are
                //looking for, this may need to be changed in the future to find the correct
                //property.
                PersistedSPOProperty propertyChange = null;
                
                for (PersistedSPOProperty nextPropertyChange : countingPersister.getPersistPropertyList()) {
                	if (nextPropertyChange.getPropertyName().equals(property.getName())) {
                		propertyChange = nextPropertyChange;
                		break;
                	}
                }
                assertNotNull("A property change event cannot be found for the property " + 
                		property.getName(), propertyChange);
                
                assertEquals(wo.getUUID(), propertyChange.getUUID());
                assertEquals(property.getName(), propertyChange.getPropertyName());
                
				assertEquals("Old value of property " + property.getName() + " was wrong, value expected was  " + oldVal + 
						" but is " + countingPersister.getLastOldValue(), oldVal, 
                		propertyChange.getOldValue());
				
				//Input streams from images are being compared by hash code not values
				if (Image.class.isAssignableFrom(property.getPropertyType())) {
					logger.debug(propertyChange.getNewValue().getClass());
					assertTrue(Arrays.equals(PersisterUtils.convertImageToStreamAsPNG(
								(Image) newVal).toByteArray(),
							PersisterUtils.convertImageToStreamAsPNG(
								(Image) converter.convertToComplexType(
										propertyChange.getNewValue(), Image.class)).toByteArray()));
				} else {
					assertEquals(newVal, propertyChange.getNewValue());
				}
                Class<? extends Object> classType;
                if (oldVal != null) {
                	classType = oldVal.getClass();
                } else {
                	classType = newVal.getClass();
                }
                assertEquals(PersisterUtils.getDataType(classType), propertyChange.getDataType());
            } catch (InvocationTargetException e) {
                logger.debug("(non-fatal) Failed to write property '"+property.getName()+" to type "+wo.getClass().getName());
            }
        }
	}

	/**
	 * Ensures that each getter and setter in the object under test is annotated
	 * in some way. This way methods that need to be annotated to be persisted
	 * will not be missed or will be defined to be skipped. The annotations are either
	 * {@link Accessor} for getters, {@link Mutator} for setters, and {@link NonProperty}
	 * that is neither an accessor or mutator but looks like one.
	 */
	public void testGettersAndSettersPersistedAnnotated() throws Exception {
		findPersistableBeanProperties(false);
	}

	/**
	 * This method will iterate through all of the methods in a class and
	 * collect all of the methods defined as setters and getters. The list will
	 * then be filtered out to only contain the bean property names of those
	 * setters and getters where each property has both a getter and setter.
	 * This will also fail if a getter or setter is found that is not properly
	 * annotated.
	 * 
	 * @param includeTransient
	 *            If true the transient properties will be included in the
	 *            resulting list. If false only the property names that are to
	 *            be persisted will be included.
	 * 
	 * @return A list of bean property names that have both a getter and setter.
	 *         The transient bean properties will be included if the transient
	 *         flag is set to true.
	 * 
	 */
	private Set<String> findPersistableBeanProperties(boolean includeTransient) throws Exception {
		SPObject objectUnderTest = getSPObjectUnderTest();
		Set<String> getters = new HashSet<String>();
		Set<String> setters = new HashSet<String>();
		for (Method m : objectUnderTest.getClass().getMethods()) {
			if (m.getName().equals("getClass")) continue;
			
			//skip non-public methods as they are not visible for persisting anyways.
			if (!Modifier.isPublic(m.getModifiers())) continue;
			//skip static methods
			if (Modifier.isStatic(m.getModifiers())) continue;
			
			if (m.getName().startsWith("get") || m.getName().startsWith("is")) {
				Class<?> parentClass = objectUnderTest.getClass();
				boolean accessor = false;
				boolean ignored = false;
				boolean isTransient = false;
				parentClass.getMethod(m.getName(), m.getParameterTypes());//test
				while (parentClass != null) {
					Method parentMethod;
					try {
						parentMethod = parentClass.getMethod(m.getName(), m.getParameterTypes());
					} catch (NoSuchMethodException e) {
						parentClass = parentClass.getSuperclass();
						continue;
					}
					if (parentMethod.getAnnotation(Accessor.class) != null) {
						accessor = true;
						if (parentMethod.getAnnotation(Transient.class) != null) {
							isTransient = true;
						}
						break;
					} else if (parentMethod.getAnnotation(NonProperty.class) != null ||
							parentMethod.getAnnotation(NonBound.class) != null) {
						ignored = true;
						break;
					}
					parentClass = parentClass.getSuperclass();
				}
				System.out.println("checking " + m.getName() + " with return type " + m.getReturnType() + " on class " + m.getDeclaringClass() + " with annotations " + Arrays.asList(m.getDeclaredAnnotations()));
				if (accessor) {
					if (includeTransient || !isTransient) {
						if (m.getName().startsWith("get")) {
							getters.add(m.getName().substring(3));
						} else if (m.getName().startsWith("is")) {
							getters.add(m.getName().substring(2));
						}
					}
				} else if (ignored) {
					//ignored so skip
				} else {
					fail("The method " + m.getName() + " is a getter that is not annotated " +
							"to be an accessor or transient. The exiting annotations are " + 
							Arrays.toString(m.getAnnotations()));
				}
			} else if (m.getName().startsWith("set")) {
				if (m.getAnnotation(Mutator.class) != null) {
					if (includeTransient || m.getAnnotation(Transient.class) == null) {
						setters.add(m.getName().substring(3));
					}
				} else if (m.getAnnotation(NonProperty.class) != null ||
						m.getAnnotation(NonBound.class) != null) {
					//ignored so skip and pass
				} else {
					fail("The method " + m.getName() + " is a setter that is not annotated " +
							"to be a mutator or transient.");
				}
			}
		}
		
		Set<String> beanNames = new HashSet<String>();
		for (String beanName : getters) {
			if (setters.contains(beanName)) {
				beanNames.add(beanName);
			}
		}
		return beanNames;
	}
}