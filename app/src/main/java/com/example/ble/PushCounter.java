package com.example.ble;

/**
 * �J�E���^�[�B
 */
public class PushCounter {

    private int count;

    /**
     * �C���X�^���X������������B
     */
    public PushCounter() {
        this.count = 0;
    }

    /**
     * �J�E���g�� +1 ����B
     *
     * @return ����
     */
    public int increment() {
        count++;
        return count;
    }

    /**
     * �J�E���g�� 0 �ɖ߂��B
     */
    public void clear() {
        count = 0;
    }

    /**
     * ���݂̃J�E���g���擾����B
     *
     * @return ���ݒl
     */
    public int get() {
        return count;
    }

    /**
     * ���݂̃J�E���g�𕶎���Ƃ��ĕԂ��B
     *
     * @return ���ݒl
     */
    @Override
    public String toString() {
        return String.valueOf(count);
    }
}
