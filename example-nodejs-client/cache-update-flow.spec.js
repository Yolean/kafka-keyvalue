
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

const mockserver = require('./mockserver');

beforeAll(() => {
  mockserver.start();
});

afterAll(() => {
  mockserver.stop();
});

describe("A complete cache update flow", () => {

  test("Check that the mock server is online on port " + mockserver.port, async () => {
    const response = await fetch(mockserver.localroot);
    expect(response.status).toEqual(200);
  });

  test("Check that pixy is online at " + PIXY_HOST, async () => {
    let response = await fetchRetry(PIXY_HOST, {
      timeout: 3,
      retries: 5,
      retryCallback: retry => console.log('Retrying pixy access', retry)
    });
    expect(response.status).toEqual(404);
  });

  test("Check existence of test topic " + TOPIC1_NAME, async () => {
    let retries = 5;
    while (true) {
      try {
        const response = await fetch(`${PIXY_HOST}/topics`, {
          method: 'GET',
          headers: {
            'Accept': 'application/json'
          }
        });
        expect(response.status).toEqual(200);
        expect(await response.json()).toContain(TOPIC1_NAME);
        retries = 0;
      } catch (e) {
        if (retries-- < 1) throw e;
        console.log('Retrying topic existence');
      }
    }
  });

  test("Check that cache is online at " + CACHE1_HOST, async () => {
    //const response = await fetch(`${CACHE1_HOST}/ready`, {
    const response = await fetchRetry(`${CACHE1_HOST}/`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json'
      },
      timeout: 3,
      retries: 10,
      retryCallback: retry => console.log('Retrying cache access', retry)
    });
    //expect(response.status).toEqual(204);
    // For now we don't have a working readiness check
    //expect(response.status).toEqual(500);
    expect(response.status).toEqual(404);
  });

  it("Starts with a produce to Pixy", async () => {
    const response = await fetch(`${PIXY_HOST}/topics/${TOPIC1_NAME}/messages?key=testasync`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({test: TEST_ID, step: 'First async produce'})
    });
    expect(response.status).toEqual(200);
    expect(await response.json()).toEqual({});
  });

  let syncResponse = null;

  it("Waits for ack from Pixy", async () => {
    syncResponse = await fetch(`${PIXY_HOST}/topics/${TOPIC1_NAME}/messages?key=test1&sync`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({test: TEST_ID, step: 'First wait for ack'})
    });
    expect(syncResponse.ok).toBeTruthy();
  });

  let latestOffset = null;

  it("Gets the produced offset from Pixy's response", async () => {
    expect(syncResponse).toBeTruthy();
    const result = await syncResponse.json();
    expect(result).toBeTruthy();
    expect(result.partition).toEqual(0);
    expect(result.offset).toBeGreaterThan(0);
    latestOffset = result.offset;
    console.log('Got offset', result.offset, 'partition', result.partition);
  });

  it("Until onUpdate is implemented we just have to wait here", done => {
    setTimeout(done, 1000);
  });

  it("The known offset should now be updated", async () => {
    const response = await fetch(`${CACHE1_HOST}/cache/v1/offset/${TOPIC1_NAME}/0`);
    expect(await response.json()).toEqual(latestOffset);
  });

  it("Finds the value in the cache", async () => {
    const response = await fetch(`${CACHE1_HOST}/cache/v1/raw/test1`);
    expect(await response.text()).toEqual(`{"test":"${TEST_ID}","step":"First wait for ack"}`);
    expect(response.status).toEqual(200);
  });

  it("Waits for the cache to notify onUpdate", done => {
    // TODO for now we can see a log message that the cache service reaches, but detection/assertion here is TODO
    setTimeout(done, 2000);
  });

  it("When the notify handler returns non-200 gets another notify", async () => {
    // We have nether retries nor error handling for onupdate requests in the cache impl now
  });

  it("Includes the updated key and the offset at which the update happened", () => {
    // Assert response body of an onupdate
  });

  it("Nothing crashes when messages lack keys, they are simply ignored", async () => {
    const response = await fetch(`${PIXY_HOST}/topics/${TOPIC1_NAME}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({test: TEST_ID, step: 'No key'})
    });
    expect(response.status).toEqual(200);
    expect(await response.json()).toEqual({});
  });

  it("Can enumerate keys", async () => {
    const response = await fetch(`${CACHE1_HOST}/cache/v1/keys`);
    expect(await response.json()).toEqual(["test1", "testasync"]);
  });

  it("Can stream values, newline separated - but note that order isn't guaranteed to match that of /keys", async () => {
    const response = await fetch(`${CACHE1_HOST}/cache/v1/values`);
    expect(await response.text()).toEqual(
      `{"test":"${TEST_ID}","step":"First wait for ack"}` + '\n' +
      `{"test":"${TEST_ID}","step":"First async produce"}` + '\n'
    );
  });

  xit("... so if we key+value streaming we should add another endpoint", async () => {
  });

});
