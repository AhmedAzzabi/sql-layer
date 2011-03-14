/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.itests.hapi.randomdb;

import com.akiban.server.InvalidOperationException;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.itests.ApiTestBase;

class Database
{
    public Database(RCTortureIT test)
    {
        this.test = test;
    }

    public void createSchema() throws InvalidOperationException
    {
        test.customerTable = test.table(
            RCTortureIT.SCHEMA, "customer",
            "cid int not null",
            "cid_copy int not null",
            "primary key(cid)",
            "index idx_cid_copy(cid_copy)");
        test.orderTable = test.table(
            RCTortureIT.SCHEMA, "order",
            "cid int not null",
            "oid int not null",
            "cid_copy int not null",
            "primary key(cid, oid)",
            "index idx_cid_copy(cid_copy)",
            "constraint __akiban_oc foreign key __akiban_oc(cid) references customer(cid)");
        test.itemTable = test.table(
            RCTortureIT.SCHEMA, "item",
            "cid int not null",
            "oid int not null",
            "iid int not null",
            "cid_copy int not null",
            "primary key(cid, oid, iid)",
            "index idx_cid_copy(cid_copy)",
            "constraint __akiban_io foreign key __akiban_io(cid, oid) references order(cid, oid)");
        test.addressTable = test.table(
            RCTortureIT.SCHEMA, "address",
            "cid int not null",
            "aid int not null",
            "cid_copy int not null",
            "primary key(cid, aid)",
            "index idx_cid_copy(cid_copy)",
            "constraint __akiban_ac foreign key __akiban_ac(cid) references customer(cid)");
    }

    public void populate() throws Exception
    {
        test.db.clear();
        Sampler sampler;
        int nCustomer = test.random.nextInt(RCTortureIT.MAX_CUSTOMERS + 1);
        sampler = new Sampler(test.random, nCustomer);
        for (int i = 0; i < nCustomer; i++) {
            long cid = sampler.take();
            NewRow row = ApiTestBase.createNewRow(test.customerTable, cid, cid);
            test.addRow(row);
        }
        int nOrder = test.random.nextInt(RCTortureIT.MAX_CUSTOMERS * RCTortureIT.MAX_ORDERS_PER_CUSTOMER + 1);
        sampler = new Sampler(test.random, nOrder);
        for (int i = 0; i < nOrder; i++) {
            long cidOid = sampler.take();
            long cid = cidOid / RCTortureIT.MAX_ORDERS_PER_CUSTOMER;
            long oid = cidOid % RCTortureIT.MAX_ORDERS_PER_CUSTOMER;
            NewRow row = ApiTestBase.createNewRow(test.orderTable, cid, oid, cid);
            test.addRow(row);
        }
        int nItem = test.random.nextInt(RCTortureIT.MAX_CUSTOMERS * RCTortureIT.MAX_ORDERS_PER_CUSTOMER * RCTortureIT.MAX_ITEMS_PER_ORDER + 1);
        sampler = new Sampler(test.random, nItem);
        for (int i = 0; i < nItem; i++) {
            long cidOidIid = sampler.take();
            long cid = cidOidIid / (RCTortureIT.MAX_ORDERS_PER_CUSTOMER * RCTortureIT.MAX_ITEMS_PER_ORDER);
            long oid = (cidOidIid / RCTortureIT.MAX_ITEMS_PER_ORDER) % RCTortureIT.MAX_ORDERS_PER_CUSTOMER;
            long iid = cidOidIid % RCTortureIT.MAX_ITEMS_PER_ORDER;
            NewRow row = ApiTestBase.createNewRow(test.itemTable, cid, oid, iid, cid);
            test.addRow(row);
        }
        int nAddress = test.random.nextInt(RCTortureIT.MAX_CUSTOMERS * RCTortureIT.MAX_ADDRESSES_PER_CUSTOMER + 1);
        sampler = new Sampler(test.random, nAddress);
        for (int i = 0; i < nAddress; i++) {
            long cidAid = sampler.take();
            long cid = cidAid / RCTortureIT.MAX_ADDRESSES_PER_CUSTOMER;
            long aid = cidAid % RCTortureIT.MAX_ADDRESSES_PER_CUSTOMER;
            NewRow row = ApiTestBase.createNewRow(test.addressTable, cid, aid, cid);
            test.addRow(row);
        }
        test.sort(test.db);
        test.printDB();
    }

    private RCTortureIT test;
}
