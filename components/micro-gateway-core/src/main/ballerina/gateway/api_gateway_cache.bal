// Copyright (c)  WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/cache;


cache:Cache gatewayKeyValidationCache;
cache:Cache invalidTokenCache;

public function initGatewayCaches() {
    gatewayKeyValidationCache = new(expiryTimeMillis = getConfigIntValue(CACHING_ID, TOKEN_CACHE_EXPIRY,
            900000), capacity = getConfigIntValue(CACHING_ID, TOKEN_CACHE_CAPACITY, 100),
        evictionFactor = getConfigFloatValue(CACHING_ID, TOKEN_CACHE_EVICTION_FACTOR, 0.25));
    invalidTokenCache = new(expiryTimeMillis = getConfigIntValue(CACHING_ID, TOKEN_CACHE_EXPIRY, 900000),
        capacity = getConfigIntValue(CACHING_ID, TOKEN_CACHE_CAPACITY, 100),
        evictionFactor = getConfigFloatValue(CACHING_ID, TOKEN_CACHE_EVICTION_FACTOR, 0.25));
}

public type APIGatewayCache object {


   public function authenticateFromGatewayKeyValidationCache(string tokenCacheKey) returns (APIKeyValidationDto|());

   public function addToGatewayKeyValidationCache (string tokenCacheKey, APIKeyValidationDto apiKeyValidationDto) ;

   public function removeFromGatewayKeyValidationCache (string tokenCacheKey);

   public function retrieveFromInvalidTokenCache(string tokenCacheKey) returns (boolean|());

   public function removeFromInvalidTokenCache (string tokenCacheKey);

   public function addToInvalidTokenCache (string tokenCacheKey, boolean authorize) ;
};

public function APIGatewayCache::authenticateFromGatewayKeyValidationCache(string tokenCacheKey) returns (APIKeyValidationDto|()) {
    match <APIKeyValidationDto> gatewayKeyValidationCache.get(tokenCacheKey){
        APIKeyValidationDto apikeyValidationDto => {
            return apikeyValidationDto;
        }
        error err => {
            return ();
        }
    }
}

public function APIGatewayCache::addToGatewayKeyValidationCache (string tokenCacheKey, APIKeyValidationDto
    apiKeyValidationDto) {
    gatewayKeyValidationCache.put(tokenCacheKey, apiKeyValidationDto);
}

public function APIGatewayCache::removeFromGatewayKeyValidationCache (string tokenCacheKey) {
    gatewayKeyValidationCache.remove(tokenCacheKey);
}

public function APIGatewayCache::retrieveFromInvalidTokenCache(string tokenCacheKey) returns (boolean|()) {
    match <boolean> invalidTokenCache.get(tokenCacheKey){
        boolean authorize => {
            return authorize;
        }
        error err => {
            return ();
        }
    }
}

public function APIGatewayCache::addToInvalidTokenCache (string tokenCacheKey, boolean authorize) {
    invalidTokenCache.put(tokenCacheKey, authorize);
}

public function APIGatewayCache::removeFromInvalidTokenCache (string tokenCacheKey) {
    invalidTokenCache.remove(tokenCacheKey);
}