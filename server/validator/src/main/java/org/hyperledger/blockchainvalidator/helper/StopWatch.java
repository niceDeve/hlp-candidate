/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hyperledger.blockchainvalidator.helper;

public class StopWatch {
    private static long startTimes[] = new long[100];
    private static long stopTimes[] = new long[100];

    public static void start(int id) {
        startTimes[id] = System.currentTimeMillis();
    }

    public static long stop(int id) {
        stopTimes[id] = System.currentTimeMillis();
        return stopTimes[id] - startTimes[id];
    }

    public static long getEllapsedTime(int id) {
        return stopTimes[id] - startTimes[id];
    }
}
