public class A5Decrypt {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java A5Decrypt <cipher_hex> <key_hex> <frame_hex>");
            System.exit(1);
        }
        try {
            String cipherHex = args[0];
            String keyHex = args[1];
            String frameHex = args[2];
            String result = A5.decrypt(cipherHex, keyHex, frameHex);
            System.out.println(result);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}