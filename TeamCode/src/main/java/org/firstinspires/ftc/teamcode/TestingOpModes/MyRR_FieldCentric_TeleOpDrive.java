package org.firstinspires.ftc.teamcode.TestingOpModes;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;


/**
 * This opmode demonstrates how one would implement field centric control using
 * `SampleMecanumDrive.java`. This file is essentially just `TeleOpDrive.java` with the addition of
 * field centric control. To achieve field centric control, the only modification one needs is to
 * rotate the input vector by the current heading before passing it into the inverse kinematics.
 * <p>
 * See lines 42-57.
 */
@TeleOp(name = "TestOpMode : MyRR Field Centric TeleOp", group = "TestOpMode")
@Disabled
public class MyRR_FieldCentric_TeleOpDrive extends LinearOpMode {

    enum GAMEPAD_LOCATION {
        RED_ALLIANCE,
        BLUE_ALLIANCE,
        AUDIENCE
    }

    Vector2d input = new Vector2d(0,0);

    @Override
    public void runOpMode() throws InterruptedException {
        // Initialize SampleMecanumDrive
        SampleMecanumDrive drive = new SampleMecanumDrive(hardwareMap);

        // We want to turn off velocity control for teleop
        // Velocity control per wheel is not necessary outside of motion profiled auto
        drive.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        // Retrieve our pose from the PoseStorage.currentPose static field
        // See AutoTransferPose.java for further details
       // drive.setPoseEstimate(PoseStorage.currentPose);
        drive.setPoseEstimate(new Pose2d(-68,24,Math.toRadians(0)));

        GAMEPAD_LOCATION gamepadLocation = GAMEPAD_LOCATION.AUDIENCE;

        //while (!(gamepad1.a || gamepad1.b || gamepad1.x)){
        ElapsedTime timer = new ElapsedTime(ElapsedTime.Resolution.SECONDS);
        timer.reset();
        while (timer.time() < 10) {
            if (gamepad1.b) { gamepadLocation = GAMEPAD_LOCATION.RED_ALLIANCE; break;}
            if (gamepad1.x) { gamepadLocation = GAMEPAD_LOCATION.BLUE_ALLIANCE;break;}
            if (gamepad1.a) { gamepadLocation = GAMEPAD_LOCATION.AUDIENCE;break;}
        }


        waitForStart();

        if (isStopRequested()) return;

        while (opModeIsActive() && !isStopRequested()) {
            // Read pose
            Pose2d poseEstimate = drive.getPoseEstimate();

            //AMJAD 11/29/2020 CORRECT SIGNS FOR ALLIANCE AND AUDIENCE DRIVE

            if (gamepadLocation == GAMEPAD_LOCATION.AUDIENCE) {

                input = new Vector2d(
                        -turboMode(getLeftStickY()) /* TODO : playingalliance modifier*/,
                        -turboMode(getLeftStickX()) /* TODO : playingalliance modifier*/
                ).rotated(-poseEstimate.getHeading());

            };

            if (gamepadLocation == GAMEPAD_LOCATION.RED_ALLIANCE) {

                input = new Vector2d(
                        turboMode(getLeftStickX()),
                        -turboMode(getLeftStickY())
                ).rotated(-poseEstimate.getHeading());


            };

            if (gamepadLocation == GAMEPAD_LOCATION.BLUE_ALLIANCE) {
                input = new Vector2d(
                        -turboMode(getLeftStickX()),
                        turboMode(getLeftStickY())
                ).rotated(-poseEstimate.getHeading());
            };

            // Create a vector from the gamepad x/y inputs
            // Then, rotate that vector by the inverse of that heading
            /*Vector2d input = new Vector2d(
                    turboMode(getLeftStickY()),
                    turboMode(getLeftStickX())
            ).rotated(-poseEstimate.getHeading());*/

            // Pass in the rotated input + right stick value for rotation
            // Rotation is not part of the rotated input thus must be passed in separately
            drive.setWeightedDrivePower(
                    new Pose2d(
                            input.getX(),
                            input.getY(),
                            -turboMode(getRightStickX())
                    )
            );

            // Update everything. Odometry. Etc.
            drive.update();

            // Print pose to telemetry
            telemetry.addData("x", poseEstimate.getX());
            telemetry.addData("y", poseEstimate.getY());
            telemetry.addData("heading", Math.toDegrees(poseEstimate.getHeading()));
            telemetry.update();
        }
    }


    /**
     * Methods to get the value of gamepad Left stick X for Pan motion X direction.
     * This is the method to apply any directional modifiers to match to the X plane of robot.
     *
     * @return gpGamepad1.left_stick_x
     */
    public double getLeftStickX() {
        return gamepad1.left_stick_x;
    }

    /**
     * Methods to get the value of gamepad Left stick Y for Pan motion Y direction.
     * This is the method to apply any directional modifiers to match to the Y plane of robot.
     *
     * @return gpGamepad1.left_stick_y
     */
    public double getLeftStickY() { return gamepad1.left_stick_y; }

    /**
     * Methods to get the value of gamepad Right stick X to keep turning.
     * This is the method to apply any directional modifiers to match to the turn direction robot.
     * No modifier needed for Hazmat Skystone Robot.
     *
     * @return gpGamepad1.right_stick_x
     */
    public double getRightStickX() {
        return gamepad1.right_stick_x;
    }

    /**
     * Methods to get the value of gamepad Right Trigger for turbo mode (max speed).
     * This is the method to apply any modifiers to match to action of turbo mode for each driver preference.
     * For Hazmat Skystone Right Trigger pressed means turbo mode on.
     *
     * @return gpGamepad1.right_trigger
     */
    public double getRightTrigger() {
        return gamepad1.right_trigger;
    }



    /**
     * Method to convert linear map from gamepad1 stick input to a cubic map
     *
     * @param stickInput input value of button stick vector
     * @return Cube of the stick input reduced to 25% speed
     */
    public double limitStick(double stickInput) {
        return (stickInput * stickInput * stickInput * 0.25);
    }

    /**
     * Method to implement turbo speed mode - from reduced speed of 25% of cubic factor to
     * 100% speed, but controlled by acceleration of the force of pressing the Right Tigger.
     *
     * @param stickInput input value of button stick vector
     * @return modified value of button stick vector
     */
    public double turboMode(double stickInput) {

        double acceleration_factor;
        double rightTriggerValue;

        double turboFactor;

        rightTriggerValue = getRightTrigger();
        acceleration_factor = 1.0 + 3.0 * rightTriggerValue;
        turboFactor = limitStick(stickInput) * acceleration_factor;
        return turboFactor;
    }

}
