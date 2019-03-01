const express = require('express');
const bodyParser = require('body-parser');
const EventEmitter = require('events');
const fetch = require('node-fetch');

const port = 8081;
const {
  PIXY_HOST = 'http://localhost:19090',
  CACHE1_HOST = 'http://localhost:19081',
  TOPIC1_NAME = 'topic1'
} = process.env;

describe('onupdate webhooks', function () {

  let server, events, getUpdatePromise;
  beforeEach(done => {

    const app = express();
    // Useful for debugging
    //app.use(require('morgan')('dev'));

    events = new EventEmitter();

    getUpdatePromise = optionalDescription => new Promise((resolve, reject) => {
      const timeoutId = setTimeout(() => reject(new Error('Timeout ' + optionalDescription)), 3000);
      events.once('onupdate', body => {
        resolve(body);
        clearInterval(timeoutId);
      });
    });

    app.post('/kafka-keyvalue/v1/updates', bodyParser.json({}), (req, res) => {
      const body = req.body;
      //console.log('Got update event from ' + req.)
      events.emit('onupdate', body);
      res.send('Update hook received');
    });

    app.post('/selfcheck', (req, res) => {
      const body = req.body;
      events.emit('onupdate', body);
      res.send('Selfcheck');
    });

    app.get('/*', (req, res) => {
      console.warn('Got other request', req.originalUrl);
      events.emit('unexpected', body);
      res.send('Unexpected update method');
    });

    app.post('/*', (req, res) => {
      console.warn('Got other request', req.originalUrl);
      res.send('Unexpected update URL');
    });

    server = app.listen(port, () => {
      console.log('Test server listening on port', port);
      done();
    });

  });

  afterEach(() => {
    server.close();
    console.log('Test server closed');
  });

  it('Gets an update upon publish', async function () {

    await new Promise(resolve => setTimeout(resolve, 500));

    const waitForSelfcheck = getUpdatePromise('selfcheck');
    const selfcheck = await fetch(`http://127.0.0.1:${port}/selfcheck`, {
      method: 'POST'
    });
    expect(await selfcheck.text()).toEqual('Selfcheck');
    expect(selfcheck.status).toEqual(200);
    await waitForSelfcheck;

    const key = 'key1';
    const waitForUpdate = getUpdatePromise('update on publish');

    console.log('Writing new message...')
    const response = await fetch(`${PIXY_HOST}/topics/${TOPIC1_NAME}/messages?key=${key}&sync`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ test: Date.now(), step: 'foo' })
    });

    expect(response.status).toEqual(200);
    const { partition, offset } = await response.json();
    console.log('Message got offset ' + offset);

    console.log('Waiting for update event...');
    const body = await waitForUpdate;

    expect(offset).toEqual(body.offset);
  });
});