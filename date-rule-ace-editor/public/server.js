var connect = require('connect');
var serveStatic = require('serve-static');
var path = require('path');
connect().use(serveStatic(__dirname),serveStatic(path.join(__dirname, 'node_modules/bootstrap/dist'))).listen(8080, function(){
    console.log('Server running on 8080...');
});

//app.use('/scripts', express.static(path.join(__dirname, 'node_modules/bootstrap/dist')));

/*
var  directory = '/path/to/Folder';
connect()
    .use(connect.static(directory))
    .listen(80);

console.log('Listening on port 80.');
*/