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
