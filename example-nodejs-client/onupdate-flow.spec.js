const express = require('express');
const bodyParser = require('body-parser');
const EventEmitter = require('events');
const fetch = require('node-fetch');

const {
  PIXY_HOST = 'http://localhost:19090',
  CACHE1_HOST = 'http://localhost:19081',
  TOPIC1_NAME = 'topic1'
} = process.env;

describe('onupdate webhooks', function () {

  let server, events, getUpdatePromise;
  beforeEach(() => {
    const app = express();
    events = new EventEmitter();

    getUpdatePromise = () => new Promise((resolve, reject) => {
      const timeoutId = setTimeout(() => reject(new Error('Timeout')), 3000);
      events.once('onupdate', body => {
        resolve(body);
        clearInterval(timeoutId);
      });
    });

    app.post('/kafka-keyvalue/v1/updates', bodyParser.json({}), (req, res) => {
      const body = req.body;
      events.emit('onupdate', body);
    });
    server = app.listen(8080);
  });

  afterEach(() => {
    server.close();
  });

  it('works', async function () {


    const key = 'key1';
    const waitForUpdate = getUpdatePromise();

    console.log('Writing new message...')
    const response = await fetch(`${PIXY_HOST}/topics/${TOPIC1_NAME}/messages?key=${key}&sync`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ test: Date.now(), step: 'foo' })
    });

    expect(response.ok).toEqual(true);
    const { partition, offset } = await response.json();
    console.log('Message got offset ' + offset);

    console.log('Waiting for update event...');
    const body = await waitForUpdate;

    expect(offset).toEqual(body.offset);
  });
});