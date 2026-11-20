public class Comp128Runner {
    public static void main(String[] args) {
        byte[] ki = hexToBytes(args[0]);
        byte[] rand = hexToBytes(args[1]);

        byte[] result = Comp128.v1(ki, rand);

        StringBuilder sb = new StringBuilder();
        for (byte b : result) {
            sb.append(String.format("%02x", b));
        }

        System.out.println(sb.toString());
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        return data;
    }
}