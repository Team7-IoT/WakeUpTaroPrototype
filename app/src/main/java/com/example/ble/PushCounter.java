package com.example.ble;

/**
 * カウンター。
 */
public class PushCounter {

    private int count;

    /**
     * インスタンスを初期化する。
     */
    public PushCounter() {
        this.count = 0;
    }

    /**
     * カウントを +1 する。
     *
     * @return 結果
     */
    public int increment() {
        count++;
        return count;
    }

    /**
     * カウントを 0 に戻す。
     */
    public void clear() {
        count = 0;
    }

    /**
     * 現在のカウントを取得する。
     *
     * @return 現在値
     */
    public int get() {
        return count;
    }

    /**
     * 現在のカウントを文字列として返す。
     *
     * @return 現在値
     */
    @Override
    public String toString() {
        return String.valueOf(count);
    }
}
