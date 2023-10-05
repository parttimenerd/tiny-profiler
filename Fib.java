public class Fib {

    public static void main(String[] args) {
        int i = fib(Integer.parseInt(args[0]));
        System.out.println(i);
    }

    static int fib(int n) {
        if (n <= 1) {
            return n;
        }
        return fib(n-1) + fib(n-2);
    }
}
