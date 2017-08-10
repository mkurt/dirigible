/*******************************************************************************
 * Copyright (c) 2017 SAP and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 * SAP - initial API and implementation
 *******************************************************************************/

package org.eclipse.dirigible.database.squle.test.sybase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.dirigible.database.squle.Squle;
import org.eclipse.dirigible.database.squle.dialects.sybase.SybaseSquleDialect;
import org.junit.Test;

public class CreateViewTest extends CreateTableTest {

	@Test
	public void createViewAsSelect() {
		createTableGeneric();
		String sql = Squle.getNative(new SybaseSquleDialect()).create().view("CUSTOMERS_VIEW").column("ID").column("FIRST_NAME").column("LAST_NAME")
				.asSelect(Squle.getDefault().select().column("*").from("CUSTOMERS").build()).build();

		assertNotNull(sql);
		assertEquals("CREATE VIEW CUSTOMERS_VIEW ( ID , FIRST_NAME , LAST_NAME ) AS SELECT * FROM CUSTOMERS", sql);
	}

}
