/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Dmitry Abramov
 * Created: 25.12.2008 16:06:25
 * $Id$
 */
package com.haulmont.cuba.gui.data.impl;

import com.haulmont.chile.core.model.Instance;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.chile.core.model.utils.InstanceUtils;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.gui.MetadataHelper;
import com.haulmont.cuba.gui.filter.QueryFilter;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.CollectionDatasourceListener;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.DatasourceListener;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

public class CollectionPropertyDatasourceImpl<T extends Entity<K>, K>
    extends
        PropertyDatasourceImpl<T>
    implements
        CollectionDatasource<T, K>
{
    private T item;
    protected boolean cascadeProperty;

    private Log log = LogFactory.getLog(CollectionPropertyDatasourceImpl.class);

    public CollectionPropertyDatasourceImpl(String id, Datasource<Entity> ds, String property) {
        super(id, ds, property);

        final MetaClass metaClass = ds.getMetaClass();
        final MetaProperty metaProperty = metaClass.getProperty(property);
        cascadeProperty = MetadataHelper.isCascade(metaProperty);
    }

    @Override
    protected void initParentDsListeners() {
        ds.addListener(new DatasourceListener<Entity>() {

            public void itemChanged(Datasource<Entity> ds, Entity prevItem, Entity item) {
                log.trace("itemChanged: prevItem=" + prevItem + ", item=" + item);

                Collection prevColl = prevItem == null ? null : (Collection) ((Instance) prevItem).getValue(metaProperty.getName());
                Collection coll = item == null ? null : (Collection) ((Instance) item).getValue(metaProperty.getName());
                reattachListeners(prevColl, coll);

                forceCollectionChanged(CollectionDatasourceListener.Operation.REFRESH);
            }

            public void stateChanged(Datasource<Entity> ds, State prevState, State state) {
                for (DatasourceListener dsListener : new ArrayList<DatasourceListener>(dsListeners)) {
                    dsListener.stateChanged(CollectionPropertyDatasourceImpl.this, prevState, state);
                }
                forceCollectionChanged(CollectionDatasourceListener.Operation.REFRESH);
            }

            public void valueChanged(Entity source, String property, Object prevValue, Object value) {
                if (property.equals(metaProperty.getName()) && !ObjectUtils.equals(prevValue, value)) {
                    log.trace("valueChanged: prop=" + property + ", prevValue=" + prevValue + ", value=" + value);

                    reattachListeners((Collection) prevValue, (Collection) value);

                    forceCollectionChanged(CollectionDatasourceListener.Operation.REFRESH);
                }
            }

            private void reattachListeners(Collection prevColl, Collection coll) {
                if (prevColl != null)
                    for (Object entity : prevColl) {
                        if (entity instanceof Instance)
                            detachListener((Instance) entity);
                    }

                if (coll != null)
                    for (Object entity : coll) {
                        if (entity instanceof Instance)
                            attachListener((Instance) entity);
                    }
            }
        });
    }

    public T getItem(K key) {
        if (key instanceof Entity)
            return (T) key;
        else {
            Collection<T> collection = __getCollection();
            for (T t : collection) {
                if (t.getId().equals(key))
                    return t;
            }
            return null;
        }
    }

    public K getItemId(T item) {
        if (item instanceof Entity)
            return item.getId();
        else
            return (K) item;
    }

    public Collection<K> getItemIds() {
        if (State.NOT_INITIALIZED.equals(ds.getState())) {
            return Collections.emptyList();
        } else {
            Collection<T> items = __getCollection();
            if (items == null)
                return Collections.emptyList();
            else {
                List<K> ids = new ArrayList(items.size());
                for (T item : items) {
                    ids.add(item.getId());
                }
                return ids;
            }
        }
    }

    @Override
    public T getItem() {
        if (State.VALID.equals(getState())) return item;
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void setItem(T item) {
        if (State.VALID.equals(getState())) {
            Object prevItem = this.item;

            if (!ObjectUtils.equals(prevItem, item)) {

                if (item instanceof Instance) {
                    final MetaClass aClass = ((Instance) item).getMetaClass();
                    if (!aClass.equals(getMetaClass())) {
                        throw new IllegalStateException(String.format("Invalid item metaClass"));
                    }
                }
                this.item = item;

                forceItemChanged(prevItem);
            }
        }
    }

    @Override
    public void refresh() {
        forceCollectionChanged(CollectionDatasourceListener.Operation.REFRESH);
    }

    public int size() {
        if (State.NOT_INITIALIZED.equals(ds.getState())) {
            return 0;
        } else {
            final Collection<T> collection = __getCollection();
            return collection == null ? 0 : collection.size();
        }
    }

    protected Collection<T> __getCollection() {
        final Instance item = (Instance) ds.getItem();
        return item == null ? null : (Collection<T>) item.getValue(metaProperty.getName());
    }

    private void checkState() {
        if (!State.VALID.equals(getState()))
            throw new IllegalStateException("Invalid datasource state: " + getState());
    }

    public void addItem(T item) throws UnsupportedOperationException {
        checkState();

        if (__getCollection() == null) {
            initCollection();
        }

        __getCollection().add(item);
        attachListener((Instance) item);

        if (ObjectUtils.equals(this.item, item)) {
            this.item = item;
        }

        modified = true;
        if (cascadeProperty) {
            final Entity parentItem = ds.getItem();
            ((DatasourceImplementation) ds).modified(parentItem);
        } else {
            modified(item);
        }

        forceCollectionChanged(CollectionDatasourceListener.Operation.ADD);
    }

    private void initCollection() {
        Instance item = (Instance) ds.getItem();
        if (item == null)
            throw new IllegalStateException("Item is null");

        Class<?> type = metaProperty.getJavaType();
        if (List.class.isAssignableFrom(type)) {
            item.setValue(metaProperty.getName(), new ArrayList());
        } else if (Set.class.isAssignableFrom(type)) {
            item.setValue(metaProperty.getName(), new HashSet());
        } else {
            throw new UnsupportedOperationException("Type " + type + " not supported, should implement List or Set");
        }
    }

    public void removeItem(T item) throws UnsupportedOperationException {
        checkState();
        __getCollection().remove(item);
        detachListener((Instance) item);

        modified = true;
        if (cascadeProperty) {
            final Entity parentItem = ds.getItem();
            ((DatasourceImplementation) ds).modified(parentItem);
        } else {
            deleted(item);
        }

        forceCollectionChanged(CollectionDatasourceListener.Operation.REMOVE);
    }

    public void updateItem(T item) {
        for (T t : __getCollection()) {
            if (t.equals(item)) {
                InstanceUtils.copy((Instance) item, (Instance) t);
            }
        }
        forceCollectionChanged(CollectionDatasourceListener.Operation.REFRESH);
    }

    public boolean containsItem(K itemId) {
        Collection<T> coll = __getCollection();
        if (coll == null)
            return false;
        
        if (itemId instanceof Entity)
            return __getCollection().contains(itemId);
        else {
            Collection<T> collection = __getCollection();
            for (T item : collection) {
                if (item.getId().equals(itemId))
                    return true;
            }
            return false;
        }
    }

    public String getQuery() {
        return null;
    }

    public QueryFilter getQueryFilter() {
        return null;
    }

    public void setQuery(String query) {
        throw new UnsupportedOperationException();
    }

    public void setQuery(String query, QueryFilter filter) {
        throw new UnsupportedOperationException();
    }

    public void setQueryFilter(QueryFilter filter) {
        throw new UnsupportedOperationException();
    }

    public int getMaxResults() {
        return 0;
    }

    public void setMaxResults(int maxResults) {
    }

    public void refresh(Map<String, Object> parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commited(Map<Entity, Entity> map) {
        for (T item : __getCollection()) {
            attachListener((Instance) item);
        }
        
        if (map.containsKey(item)) {
            item = (T) map.get(item);
        }

        modified = false;
        clearCommitLists();
    }

    protected void forceCollectionChanged(CollectionDatasourceListener.Operation operation) {
        for (DatasourceListener dsListener : dsListeners) {
            if (dsListener instanceof CollectionDatasourceListener) {
                ((CollectionDatasourceListener) dsListener).collectionChanged(this, operation);
            }
        }
    }

    public boolean isSoftDeletion() {
        return false;
    }

    public void setSoftDeletion(boolean softDeletion) {
    }
}
