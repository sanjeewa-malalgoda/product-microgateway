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

import ballerina/io;
import ballerina/http;
import ballerina/time;


public type AnalyticsRequestFilter object {

    public function filterRequest(http:Request request, http:FilterContext context) returns http:FilterResult {
        match <boolean> context.attributes[FILTER_FAILED] {
            boolean failed => {
                if (failed) {
                    return createFilterResult(true, 200, "Skipping filter due to parent filter has returned false");
                }
            } error err => {
            //Nothing to handle
            }
        }
        http:FilterResult requestFilterResult;
        AnalyticsRequestStream requestStream = generateRequestEvent(request, context);
        EventDTO eventDto = generateEventFromRequest(requestStream);
        eventStream.publish(eventDto);
        requestFilterResult = { canProceed: true, statusCode: 200, message: "Analytics filter processed." };
        return requestFilterResult;

    }

};