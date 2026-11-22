// Copy of init-mongo-init.js from the repository root, which docker-compose mounts into the
// mongodb container. The chart keeps its own copy because Helm cannot read files outside the chart
// directory; `make k3d-up` installs the chart on its own.

// Create product database user
db = db.getSiblingDB('productdb');
db.createUser({
  user: 'productuser',
  pwd: 'dev123',
  roles: [{ role: 'readWrite', db: 'productdb' }]
});

// Create notification database user
db = db.getSiblingDB('notificationdb');
db.createUser({
  user: 'notificationuser',
  pwd: 'dev123',
  roles: [{ role: 'readWrite', db: 'notificationdb' }]
});
