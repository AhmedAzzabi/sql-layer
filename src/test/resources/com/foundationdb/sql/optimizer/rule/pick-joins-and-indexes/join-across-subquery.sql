SELECT name, (SELECT COUNT(*) FROM orders, items WHERE orders.oid = items.oid AND orders.cid = customers.cid) FROM customers