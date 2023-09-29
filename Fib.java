public class Fib {

    public static void main(String[] args) {
        System.out.println(fib(Integer.parseInt(args[0])));
    }

    static int fib(int n) {
        if (n <= 1) {
            return n;
        }
        return fib(n-1) + fib(n-2);
    }
}
