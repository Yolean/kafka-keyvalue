// wait for services in ../build-contracts

const {
  PIXY_HOST = 'http://localhost:19090',
  CACHE1_HOST = 'http://localhost:19081',
  TOPIC1_NAME = 'topic1',
  TEST_ID = '' + new Date().toISOString()
} = process.env;

const fetch = require('node-fetch');

// retry on no connection, but not on any status code
// There's node-fetch-retry and node-fetch-plus if we want libs
const fetchRetry = async (url, opts) => {
  let retry = opts && opts.retries || 3
  while (retry > 0) {
    try {
      return await fetch(url, opts)
    } catch(e) {
      if (opts.retryCallback) {
          opts.retryCallback(retry)
      }
      retry = retry - 1
      if (retry == 0) {
          throw e
      }
    }
  }
};

module.exports = async function() {
  console.log(' Looking for test stack...');

  //test("Check that pixy is online at " + PIXY_HOST, async () => {
  let response = await fetchRetry(PIXY_HOST, {
    timeout: 3,
    retries: 10,
    retryCallback: retry => console.log(new Date(), 'Retrying pixy access', retry)
  });
  //expect(response.status).toEqual(404);
  //});

};
