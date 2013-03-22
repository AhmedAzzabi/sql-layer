
package com.akiban.util;

public final class ThreadlessRandom {

    private int rand;

    public ThreadlessRandom() {
        this( (int)System.currentTimeMillis() );
    }

    public ThreadlessRandom(int seed) {
        this.rand = seed;
    }

    /**
     * Returns the next random number in the sequence
     * @return a pseudo-random number
     */
    public int nextInt() {
        return ( rand = rand(rand) ); // probably the randiest line in the code
    }

    /**
     * Returns the next random number in the sequence, bounded by the given bounds.
     * @param min the minimum value of the random number, inclusive
     * @param max the maximum value of the random number, isShared
     * @return a number N such that {@code min <= N < max}
     * @throws IllegalArgumentException if {@code min >= max}
     */
    public int nextInt(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException(String.format("bad range: [%d, %d)", min, max));
        }
        int range = max - min;
        int ret = nextInt();
        ret = Math.abs(ret) % range;
        return ret + min;
    }

    /**
     * Quick and dirty pseudo-random generator with no concurrency ramifications.
     * Taken from JCIP; the source is public domain. See:
     * http://jcip.net/listings.html listing 12.4.
     * @param seed the random's seed
     * @return the randomized result
     */
    public static int rand(int seed) {
        seed ^= (seed << 6);
        seed ^= (seed >>> 21);
        seed ^= (seed << 7);
        return seed;
    }
}
