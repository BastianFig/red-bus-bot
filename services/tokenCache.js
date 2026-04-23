let cache = { token: null, expiresAt: 0 };

const isValid = () => cache.token && Date.now() < cache.expiresAt;
const getToken = () => cache.token;
const setToken = (token) => {
  cache = { token, expiresAt: Date.now() + 10 * 60 * 1000 };
};

module.exports = { isValid, getToken, setToken };