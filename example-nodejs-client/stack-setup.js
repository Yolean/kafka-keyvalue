// Doens't actually set up anything
// Waits for services in ../build-contracts

const {
  PIXY_HOST = 'http://localhost:19090',
  CACHE1_HOST = 'http://localhost:19081',
  TOPIC1_NAME = 'topic1',
  TEST_ID = '' + new Date().toISOString()
} = process.env;

const fetch = require('node-fetch');
const retryx = require('retryx');

module.exports = async function() {
  console.log(' Looking for test stack...');

  let pixy = await retryx(() => fetch(PIXY_HOST, {
  }),{
    maxTries: 10,
    beforeWait: n => console.log('Try', n, 'pixy access at', PIXY_HOST)
  });
  if (pixy.status !== 404) console.warn('Unexpected pixy response status', pixy.status, PIXY_HOST);
  console.log('Pixy is reachable at', PIXY_HOST);

  const cache = await retryx(() => fetch(`${CACHE1_HOST}/`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json'
    }
  }),{
    maxTries: 10,
    beforeWait: n => console.log('Try', n, 'cache access at', CACHE1_HOST)
  });
  if (cache.status !== 404) console.warn('Unexpected cache service response status', cache.status, PIXY_HOST);
  console.log('Cache service is reachable at', CACHE1_HOST);

  const topic = await retryx(() => fetch(`${PIXY_HOST}/topics`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json'
      }
    }).then(response => response.json().then(topics => {
        if (!topics || !topics.length) return Promise.reject('Unexpected topics response ' + topics);
        if (topics.indexOf(TOPIC1_NAME) === -1) return Promise.reject('Topic ' + TOPIC1_NAME + ' not found in: ' + topics);
        console.log('Found test topic ' + TOPIC1_NAME + ' in pixy\'s topic list');
        Promise.resolve(response);
    }))
  ,{
    maxTries: 10,
    beforeWait: n => console.log('Try', n, 'topic existence', TOPIC1_NAME)
  });

};
