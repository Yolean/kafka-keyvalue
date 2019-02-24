// wait for services in ../build-contracts
module.exports = async function() {
  console.log(' Looking for test stack...');
  await new Promise(resolve => setTimeout(resolve, 1));
  throw new Error('Setup not implemented');
};
