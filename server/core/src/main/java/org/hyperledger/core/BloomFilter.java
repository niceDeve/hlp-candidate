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
package org.hyperledger.core;

import org.bouncycastle.util.Arrays;
import org.hyperledger.common.Outpoint;
import org.hyperledger.common.WireFormat;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A bloom filter
 */
public class BloomFilter {
    public enum UpdateMode {
        none, all, keys
    }

    private final byte[] filter;
    private final int hashFunctions;
    private final int tweak;
    private final UpdateMode update;

    private static final int MAX_FILTER_SIZE = 36000;
    private static final int MAX_HASH_FUNCS = 50;

    /**
     * create a bloom filter of optimal size and hash functions for
     *
     * @param n                        - number of data stored
     * @param falsePositiveProbability - target false positive probability less than this
     * @param update                   - a flag if bloom filter should be updated with transactions it matches.
     * @return a new empty bloom filter
     */
    public static BloomFilter createOptimalFilter(int n, double falsePositiveProbability, UpdateMode update) {
        return createOptimalFilter(n, falsePositiveProbability, Math.abs(new SecureRandom().nextInt()), update);
    }

    /**
     * create a bloom filter of optimal size and hash functions for
     *
     * @param n                        - number of data stored
     * @param falsePositiveProbability - target false positive probability less than this
     * @param tweak                    - a tweak to obfuscate what information the filter was built on
     * @param update                   - a flag if bloom filter should be updated with transactions it matches.
     * @return a new empty bloom filter
     */
    public static BloomFilter createOptimalFilter(int n, double falsePositiveProbability, int tweak, UpdateMode update) {
        // http://en.wikipedia.org/wiki/Bloom_filter#Probability_of_false_positives
        double ln2 = Math.log(2.0);
        int mod = Math.max(1, (int) Math.min((-n * Math.log(falsePositiveProbability) / (ln2 * ln2)) / 8.0, MAX_FILTER_SIZE));
        int hashFunctions = Math.max(1, Math.min((int) (mod * 8.0 / n * ln2), MAX_HASH_FUNCS));
        return new BloomFilter(new byte[mod], hashFunctions, tweak, update);
    }

    /**
     * Get false positive probability for given filter if fed with n input data.
     *
     * @param n
     * @return
     */
    public double getFalsePositiveProbability(int n) {
        return Math.pow(1 - Math.pow(Math.E, -1.0 * (hashFunctions * n) / (filter.length * 8)), hashFunctions);
    }

    /**
     * Create a bloom filter
     *
     * @param data          filter content
     * @param hashFunctions - number of hash functions to use
     * @param tweak         - obfuscation parameter
     * @param update        - a flag if transactions identified by the filter should update the filter
     */
    public BloomFilter(byte[] data, int hashFunctions, int tweak, UpdateMode update) {
        filter = Arrays.clone(data);
        this.hashFunctions = Math.min(hashFunctions, MAX_HASH_FUNCS);
        this.tweak = tweak;
        this.update = update;
    }

    /**
     * Serialize and addTransaction a transaction output to a filter
     *
     * @param outpoint
     * @return
     */
    public void addOutpoint(Outpoint outpoint) {
        add(outpoint.toWire());
    }

    /**
     * Check if the filter contains a transaction output with positive probability
     *
     * @param outpoint
     * @return
     */
    public boolean containsOutpoint(Outpoint outpoint) {
        return contains(outpoint.toWire());
    }

    /**
     * explicitelly set a filter bit
     *
     * @param n
     */
    private void setBit(int n) {
        filter[n >>> 3] |= 1 << (7 & n);
    }

    /**
     * test if a bit is set in the filter mask
     *
     * @param n
     * @return
     */
    private boolean testBit(int n) {
        return (filter[n >>> 3] & 1 << (7 & n)) != 0;
    }

    /**
     * addTransaction some data to the filter.
     *
     * @param data
     * @return
     */
    public int add(byte[] data) {
        for (int i = 0; i < hashFunctions; ++i) {
            setBit((int) ((murmurhash3bit(i, data, tweak) & 0xFFFFFFFFL) % (filter.length * 8)));
        }
        return Arrays.hashCode(data);
    }

    /**
     * Ask if a data was added to the filter. The filter might give false positive answers, but will not confirm existence of data that was not added.
     *
     * @param data
     * @return
     */
    public boolean contains(byte[] data) {
        for (int i = 0; i < hashFunctions; ++i) {
            if (!testBit((int) ((murmurhash3bit(i, data, tweak) & 0xFFFFFFFFL) % (filter.length * 8)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Precompute hashes used to set mask bits for a data.
     *
     * @param data
     * @param tweak
     * @return
     */
    public static List<Integer> precomputeHashes(byte[] data, long tweak) {
        ArrayList<Integer> list = new ArrayList<>(MAX_HASH_FUNCS);
        for (int i = 0; i < MAX_HASH_FUNCS; ++i) {
            list.add(murmurhash3bit(i, data, tweak));
        }
        return list;
    }

    /**
     * check if the filter contains data with precomputed hashes.
     *
     * @param hashes
     * @return
     */
    public boolean contains(List<Integer> hashes) {
        Iterator<Integer> ni = hashes.iterator();
        for (int i = 0; i < hashFunctions; ++i) {
            if (!testBit((int) ((ni.next() & 0xFFFFFFFFL) % (filter.length * 8)))) {
                return false;
            }
        }
        return true;
    }

    private static int murmurhash3bit(int hashNum, byte[] data, long tweak) {
        return murmurhash3(data, 0, data.length, (int) (hashNum * 0xFBA4C795L + tweak));
    }

    /**
     * write filter to wire
     *
     * @param writer
     */
    public void toWire(WireFormat.Writer writer) throws IOException {
        writer.writeVarBytes(filter);
        writer.writeUint32(hashFunctions);
        writer.writeUint32(tweak);
        writer.writeByte(update.ordinal());
    }

    /**
     * reconstruct filter from wire format
     *
     * @param reader
     * @return
     */
    public static BloomFilter fromWire(WireFormat.Reader reader) throws IOException {
        byte[] data = reader.readVarBytes();
        int hashFunctions = reader.readUint32();
        int tweak = reader.readUint32();
        int update = reader.readByte();
        return new BloomFilter(data, hashFunctions, tweak, UpdateMode.values()[update]);
    }

    /*
     * This code is public domain.
     *
     * The MurmurHash3 algorithm was created by Austin Appleby and put into the public domain. See http://code.google.com/p/smhasher/
     *
     * This java port was authored by Yonik Seeley and was placed into the public domain per
     * https://github.com/yonik/java_util/blob/master/src/util/hash/MurmurHash3.java.
     */
    private static int murmurhash3(byte[] data, int offset, int len, int seed) {
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;

        int h1 = seed;
        int roundedEnd = offset + (len & 0xfffffffc); // round down to 4 byte block

        for (int i = offset; i < roundedEnd; i += 4) {
            // little endian load order
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
            k1 *= c1;
            k1 = (k1 << 15) | (k1 >>> 17); // ROTL32(k1,15);
            k1 *= c2;

            h1 ^= k1;
            h1 = (h1 << 13) | (h1 >>> 19); // ROTL32(h1,13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // tail
        int k1 = 0;

        switch (len & 0x03) {
            case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
                // fallthrough
            case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
                // fallthrough
            case 1:
                k1 |= data[roundedEnd] & 0xff;
                k1 *= c1;
                k1 = (k1 << 15) | (k1 >>> 17); // ROTL32(k1,15);
                k1 *= c2;
                h1 ^= k1;
            default:
        }

        // finalization
        h1 ^= len;

        // fmix(h1);
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }

    public byte[] getFilter() {
        return filter.clone();
    }

    /**
     * get the number of hash functions used
     *
     * @return
     */
    public long getHashFunctions() {
        return hashFunctions;
    }

    /**
     * get obfuscation parameter
     *
     * @return
     */
    public long getTweak() {
        return tweak;
    }

    /**
     * get filter update mode
     *
     * @return
     */
    public UpdateMode getUpdateMode() {
        return update;
    }
}
