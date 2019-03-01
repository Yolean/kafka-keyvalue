
// Note: this server's port is currently not exposed in the docker-compose test setup,
// because onpudate-flow.spec.js has its own server

const port = 8082,
  express = require('express'),
  app = express(),
  morgan = require('morgan');

app.use(morgan('dev'));

app.get('/', function (req, res) {
  res.send('Hello World!');
});

let server = null;

function start() {
  server = app.listen(port, function () {
    console.log('listening on port', port);
  });
}

function stop() {
  if (!server) throw new Error('Server not started');
  server.close();
  server = null;
}

module.exports = {
  start,
  stop,
  app,
  port,
  localroot: `http://localhost:${port}`
};
