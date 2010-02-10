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

package ca.sqlpower.dao;

import java.beans.PropertyChangeEvent;
import java.util.List;

import ca.sqlpower.dao.SPPersister.DataType;
import ca.sqlpower.dao.helper.PersisterHelperFinder;
import ca.sqlpower.dao.session.SessionPersisterSuperConverter;
import ca.sqlpower.object.SPChildEvent;
import ca.sqlpower.object.SPListener;
import ca.sqlpower.object.SPObject;
import ca.sqlpower.sqlobject.SQLObject;
import ca.sqlpower.util.TransactionEvent;

/**
 * This generic listener will use the persister helper factory given to it to
 * make persist calls when events are fired on the object being listened to.
 */
public class SPPersisterListener implements SPListener {

	/**
	 * This persister will have persist calls made on it when the object(s) this
	 * listener is attached to fires events.
	 */
	private final SPPersister target;

	/**
	 * A class that can convert a complex object into a basic representation
	 * that can be placed in a string and can also convert the string
	 * representation back into the complex object.
	 */
	private final SessionPersisterSuperConverter converter;

	/**
	 * This listener can be attached to a hierarchy of objects to persist events
	 * to the target persister contained in the given persister helper factory.
	 * 
	 * @param target
	 *            The target persister that will be sent persist calls.
	 * @param converter
	 *            A converter to convert complex types of objects into simple
	 *            objects so the object can be passed or persisted.
	 */
	public SPPersisterListener(SPPersister target, SessionPersisterSuperConverter converter) {
		this.target = target;
		this.converter = converter;
		
	}

	public void childAdded(SPChildEvent e) {
		e.getChild().addSPListener(this);
		persistObject(e.getChild(), e.getIndex());
	}

	/**
	 * This persists a given object and all of its descendants to the target
	 * persister in this listener. Each object in the descendant tree will have
	 * one persist object call made and any number of additional persist
	 * property calls as needed. This can be useful for persisting an entire
	 * tree of objects to the JCR as an initial commit.
	 * 
	 * @param o
	 *            The object to be persisted.
	 * @param index
	 *            the index the object is located in its parent's list of
	 *            children of the same object type.
	 */
	public void persistObject(SPObject o, int index) {
		this.transactionStarted(TransactionEvent.createStartTransactionEvent(this, 
			"Persisting " + o.getName() + " and its descendants."));

		try {
			PersisterHelperFinder.findPersister(o.getClass()).persistObject(o, index, target, converter);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		List<? extends SPObject> children;
		if (o instanceof SQLObject) {
			children = ((SQLObject) o).getChildrenWithoutPopulating();
		} else {
			children = o.getChildren();
		}
		for (SPObject child : children) {
			int childIndex = 0;
			if (o instanceof SQLObject) {
				childIndex = ((SQLObject) o).getChildrenWithoutPopulating(child.getClass()).indexOf(child);
			} else {
				childIndex = o.getChildren(child.getClass()).indexOf(child);
			}
			persistObject(child, childIndex);
		}

		this.transactionEnded(TransactionEvent.createEndTransactionEvent(this));
	}

	public void childRemoved(SPChildEvent e) {
		try {
			target.removeObject(e.getSource().getUUID(), e.getChild().getUUID());
			e.getChild().removeSPListener(this);
		} catch (SPPersistenceException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void transactionEnded(TransactionEvent e) {
		try {
			target.commit();
		} catch (SPPersistenceException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void transactionRollback(TransactionEvent e) {
		target.rollback();
	}

	public void transactionStarted(TransactionEvent e) {
		try {
			target.begin();
		} catch (SPPersistenceException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void propertyChanged(PropertyChangeEvent evt) {
		DataType dataType;
		if (evt.getNewValue() != null) {
			dataType = PersisterUtils.getDataType(evt.getNewValue().getClass());
		} else {
			dataType = PersisterUtils.getDataType(evt.getOldValue() == null ? 
					Void.class : evt.getOldValue().getClass());
		}
		try {
			target.persistProperty(((SPObject) evt.getSource()).getUUID(), 
					evt.getPropertyName(), dataType, evt.getOldValue(), 
					evt.getNewValue());
		} catch (SPPersistenceException e) {
			throw new RuntimeException(e);
		}
	}

}