

 import java.util.Scanner;

public class GluteWorkout {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String[] workouts = {
            "Glute Bridge - 3 sets of 15 reps",
            "Donkey Kicks - 3 sets of 12 reps",
            "Fire Hydrants - 3 sets of 12 reps",
            "Bulgarian Split Squats - 3 sets of 10 reps each leg",
            "Hip Thrusts - 3 sets of 15 reps"
        };

        System.out.println("💪 Welcome to your Glute Workout Tracker!");
        System.out.println("Here’s your workout plan for today:\n");

        for (int i = 0; i < workouts.length; i++) {
            System.out.println((i + 1) + ". " + workouts[i]);
        }

        System.out.println("\nType 'start' to begin your workout.");
        String input = scanner.nextLine();

        if (input.equalsIgnoreCase("start")) {
for (String workout : workouts) {
                System.out.println("Starting: " + workout);
                simulateTimer(10); // Simulate 10-second rest/workSystem.out.println("Done ✅\n");
            }
            System.out.println("🎉 Workout complete! Great job!");
        } else {
            System.out.println("Workout cancelled.");
        }

        scanner.close();
    }

    private static void simulateTimer(int seconds) {
        try {
            for (int i = seconds; i > 0; i--) {
                System.out.print("⏱ " + i + "s\r");
                Thread.sleep(1000);
            }
            System.out.println();
        } catch (InterruptedException e) {
            System.out.println("Timer error.");
        }
    }
}
