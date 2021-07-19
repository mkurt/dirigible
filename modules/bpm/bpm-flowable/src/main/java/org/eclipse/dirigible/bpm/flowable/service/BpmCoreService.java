/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company and Eclipse Dirigible contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-FileCopyrightText: 2021 SAP SE or an SAP affiliate company and Eclipse Dirigible contributors
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.dirigible.bpm.flowable.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

import org.eclipse.dirigible.api.v3.security.UserFacade;
import org.eclipse.dirigible.bpm.api.BpmException;
import org.eclipse.dirigible.bpm.flowable.api.IBpmCoreService;
import org.eclipse.dirigible.bpm.flowable.definition.BpmDefinition;
import org.eclipse.dirigible.database.persistence.PersistenceManager;

/**
 * The Class BpmCoreService.
 */
@Singleton
public class BpmCoreService implements IBpmCoreService {

	@Inject
	private DataSource dataSource;

	@Inject
	private PersistenceManager<BpmDefinition> bpmPersistenceManager;

	// BPM

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.dirigible.bpm.flowable.api.IBpmCoreService#createBpm(java.lang.String, java.lang.String)
	 */
	@Override
	public BpmDefinition createBpm(String location, String hash) throws BpmException {
		BpmDefinition bpmDefinition = new BpmDefinition();
		bpmDefinition.setLocation(location);
		bpmDefinition.setHash(hash);
		bpmDefinition.setCreatedBy(UserFacade.getName());
		bpmDefinition.setCreatedAt(new Timestamp(new java.util.Date().getTime()));

		try {
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				bpmPersistenceManager.insert(connection, bpmDefinition);
				return bpmDefinition;
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (SQLException e) {
			throw new BpmException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.dirigible.bpm.flowable.api.IBpmCoreService#getBpm(java.lang.String)
	 */
	@Override
	public BpmDefinition getBpm(String location) throws BpmException {
		try {
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				return bpmPersistenceManager.find(connection, BpmDefinition.class, location);
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (SQLException e) {
			throw new BpmException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.dirigible.bpm.flowable.api.IBpmCoreService#removeBpm(java.lang.String)
	 */
	@Override
	public void removeBpm(String location) throws BpmException {
		try {
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				bpmPersistenceManager.delete(connection, BpmDefinition.class, location);
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (SQLException e) {
			throw new BpmException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.dirigible.bpm.flowable.api.IBpmCoreService#updateBpm(java.lang.String, java.lang.String)
	 */
	@Override
	public void updateBpm(String location, String hash) throws BpmException {
		try {
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				BpmDefinition bpmDefinition = getBpm(location);
				bpmDefinition.setHash(hash);
				bpmPersistenceManager.update(connection, bpmDefinition);
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (SQLException e) {
			throw new BpmException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.dirigible.bpm.flowable.api.IBpmCoreService#getBpmList()
	 */
	@Override
	public List<BpmDefinition> getBpmList() throws BpmException {
		try {
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				return bpmPersistenceManager.findAll(connection, BpmDefinition.class);
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (SQLException e) {
			throw new BpmException(e);
		}
	}

	@Override
	public boolean existsBpm(String location) throws BpmException {
		return getBpm(location) != null;
	}

}
