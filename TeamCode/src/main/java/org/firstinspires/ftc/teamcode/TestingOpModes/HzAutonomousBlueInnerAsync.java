package org.firstinspires.ftc.teamcode.TestingOpModes;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.SubSystems.HzArm;
import org.firstinspires.ftc.teamcode.SubSystems.HzAutoControl;
import org.firstinspires.ftc.teamcode.SubSystems.HzDrive;
import org.firstinspires.ftc.teamcode.SubSystems.HzGameField;
import org.firstinspires.ftc.teamcode.SubSystems.HzGamepad;
import org.firstinspires.ftc.teamcode.SubSystems.HzIntake;
import org.firstinspires.ftc.teamcode.SubSystems.HzLaunchController;
import org.firstinspires.ftc.teamcode.SubSystems.HzLauncher;
import org.firstinspires.ftc.teamcode.SubSystems.HzMagazine;
import org.firstinspires.ftc.teamcode.SubSystems.HzVuforia;

import static com.qualcomm.robotcore.util.ElapsedTime.Resolution.MILLISECONDS;


/**
 * This opmode demonstrates how one would implement field centric control using
 * `SampleMecanumDrive.java`. This file is essentially just `TeleOpDrive.java` with the addition of
 * field centric control. To achieve field centric control, the only modification one needs is to
 * rotate the input vector by the current heading before passing it into the inverse kinematics.
 * <p>
 * See lines 42-57.
 *
 * setAutoMoveArmPickWobble()
 * setAutoMoveArmDropWobbleRing()
 *
 */
@Autonomous(name = "Hazmat Blue Inner Autonomous", group = "00-Autonomous" , preselectTeleOp = "Hazmat TeleOp RR")
@Disabled
public class HzAutonomousBlueInnerAsync extends LinearOpMode {

    public boolean HzDEBUG_FLAG = true;

    public HzGamepad hzGamepad;
    public HzAutoControl hzAutoControl;
    public HzDrive hzDrive;
    public HzMagazine hzMagazine;
    public HzIntake hzIntake;
    public HzLaunchController hzLaunchController;
    public HzLauncher hzLauncher;
    public HzArm hzArm;

    public HzVuforia hzVuforia;
    public Pose2d startPose = HzGameField.BLUE_INNER_START_LINE;

    boolean parked = false ;
    boolean autonomousStarted = false;

    public HzGameField.TARGET_ZONE targetZone = HzGameField.TARGET_ZONE.A;
    public HzVuforia.ACTIVE_WEBCAM activeWebcam = HzVuforia.ACTIVE_WEBCAM.LEFT;

    @Override
    public void runOpMode() throws InterruptedException {
        // Initialize SampleMecanumDrive
        hzDrive = new HzDrive(hardwareMap);
        hzMagazine = new HzMagazine(hardwareMap);
        hzIntake = new HzIntake(hardwareMap);

        hzLauncher = new HzLauncher(hardwareMap);
        hzArm = new HzArm(hardwareMap);
        hzLaunchController = new HzLaunchController(hardwareMap, hzLauncher, hzIntake, hzMagazine, hzDrive);
        hzGamepad = new HzGamepad(gamepad1,hzDrive,hzMagazine,hzIntake,hzLaunchController,hzLauncher,hzArm);
        hzAutoControl = new HzAutoControl(hzDrive,hzMagazine,hzIntake,hzLaunchController,hzLauncher,hzArm);

        selectGamePlan();
        //HzGameField.playingAlliance = HzGameField.PLAYING_ALLIANCE.BLUE_ALLIANCE;
        //startPose = HzGameField.BLUE_INNER_START_LINE;
        //activeWebcam = HzVuforia.ACTIVE_WEBCAM.LEFT;
        //hzAutoControl.autoLaunchAim = HzAutoControl.AutoLaunchAim.HIGHGOAL;
        //hzAutoControl.autoLaunchAim = HzAutoControl.AutoLaunchAim.POWERSHOT;
        //hzAutoControl.pickRingFromTargetMarker = true;
        //hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal = true;

        hzVuforia = new HzVuforia(hardwareMap, activeWebcam);

        // Initiate Camera even before Start is pressed.
        //waitForStart();

        hzVuforia.activateVuforiaTensorFlow();
        boolean vuforiaTensorFlowActivated = true;

        hzDrive.getLocalizer().setPoseEstimate(startPose);

        hzIntake.setIntakeReleaseHold();
        hzAutoControl.setMagazineToLaunch();

        telemetry.addData("Waiting for start to be pressed.","Robot is ready!");
        telemetry.update();

        if (isStopRequested()) return;

        while (!isStopRequested()) {
            //Init is pressed at this time, and start is not pressed yet

            //Run Vuforia Tensor Flow
            targetZone = hzVuforia.runVuforiaTensorFlow();

            if (HzDEBUG_FLAG) {
                printDebugMessages();
                telemetry.update();
            }
            hzLaunchController.launchMode = HzLaunchController.LAUNCH_MODE.MANUAL;

            buildAutoBlueInnerTrajectory();


            while (opModeIsActive() && !isStopRequested() && !parked) {
                if (vuforiaTensorFlowActivated == true) {
                    hzVuforia.deactivateVuforiaTensorFlow();
                    vuforiaTensorFlowActivated = false;
                }
                autonomousStarted = true;
                runAutoBlueInner();
                hzDrive.update();
                hzAutoControl.runAutoControl();

                //Move to Launching Position
                HzGameField.currentPose = hzDrive.getPoseEstimate();
                HzGameField.poseSetInAutonomous = true;

                if (HzDEBUG_FLAG) {
                    printDebugMessages();
                    telemetry.update();
                }
            }

        }

        hzIntake.setIntakeReleaseOpen();

        // Transfer the current pose to PoseStorage so we can use it in TeleOp
        HzGameField.currentPose = hzDrive.getPoseEstimate();
        HzGameField.poseSetInAutonomous = true;
    }

    enum AutoStepState {
        START,
        INITIATE,
        TRAJ_LAUNCH_RINGS_HIGHGOAL,
        LAUNCH_RINGS_HIGHGOAL,
        TRAJ_LAUNCH_RING_POWERSHOT1,
        LAUNCH_RINGS_POWERSHOT1,
        TRAJ_LAUNCH_RING_POWERSHOT2,
        LAUNCH_RINGS_POWERSHOT2,
        TRAJ_LAUNCH_RING_POWERSHOT3,
        LAUNCH_RINGS_POWERSHOT3,
        TRAJ_SAFE_POSITION_BEFORE_WOBBLE_TARGET_DROP,
        TRAJ_WOBBLE_DROP_POSITION,
        DROP_WOBBLE_GOAL_ON_TARGET,
        TRAJ_SAFE_POSITION_AFTER_WOBBLE_TARGET_DROP,
        TRAJ_PARK_WITHOUT_PICK_RING,
        //TRAJ_SAFE_POSITION_BEFORE_PICK_RINGS,
        START_INTAKE,
        TRAJ_PICK_RINGS_FROM_TARGET_MARK,
        TRAJ_TURN_TO_HIGH_GOAL,
        LAUNCH_RINGS_HIGHGOAL_SECOND,
        TRAJ_SAFE_POSITION_BEFORE_PARK,
        TRAJ_PARK,
        END,
        IDLE
    }
    AutoStepState currentAutoStepState = AutoStepState.START;
    AutoStepState buildAutoStepState = AutoStepState.START;

    Trajectory trajLaunchRingsHighGoal;
    Trajectory trajLaunchRingPowershot1;
    //Trajectory trajLaunchRingPowershot2;
    //Trajectory trajLaunchRingPowershot3;
    Trajectory trajSafePositionBeforeWobbleTargetDrop;
    Trajectory trajWobbleDropPosition;
    Trajectory trajSafePositionAfterWobbleTargetDrop;
    Trajectory trajParkWithoutPickRing;
    //Trajectory trajSafe_Position_Before_Pick_Rings;
    Trajectory trajPickRingsFromTargetMark;
    Trajectory trajTurnToHighGoal;
    Trajectory trajSafePostionBeforePark;
    Trajectory trajPark;
    Trajectory trajTemp;

    Pose2d lastPose = startPose;
    double turnAnglePowershot12 = Math.toRadians(-5);
    double turnAnglePowershot23 = Math.toRadians(-5);


    public void buildAutoBlueInnerTrajectory(){

        if (hzAutoControl.autoLaunchAim == HzAutoControl.AutoLaunchAim.HIGHGOAL) {
            trajTemp = hzDrive.trajectoryBuilder(hzDrive.getPoseEstimate())
                    //.lineTo(new Vector2d(-40,6))
                    .lineToLinearHeading(new Pose2d(-40,6,Math.toRadians(45)))
                    .build();
            lastPose = trajTemp.end();
            trajLaunchRingsHighGoal = hzDrive.trajectoryBuilder(lastPose)
                    //.lineToConstantHeading(new Vector2d(-40,14))
                    //.splineToLinearHeading(new Pose2d(-10,14,Math.toRadians(15)),Math.toRadians(0))
                    .lineToLinearHeading(new Pose2d(-10,14,Math.toRadians(15)))
                    //.splineTo(new Vector2d(-10,14),Math.toRadians(15))
                    .build();
            lastPose = trajLaunchRingsHighGoal.end();
        } else { //hzAutoControl.autoLaunchAim == HzAutoControl.AutoLaunchAim.POWERSHOT
            trajLaunchRingPowershot1 = hzDrive.trajectoryBuilder(startPose)
                    //.lineToSplineHeading(new Pose2d(-10,13,Math.toRadians(5)))
                    .splineToLinearHeading(new Pose2d(-10,14,Math.toRadians(5)),Math.toRadians(0))
                    //.splineTo(new Vector2d(-10,14),Math.toRadians(5))
                    .build();
            lastPose = trajLaunchRingPowershot1.end();
            //Turn to Powershot2
            lastPose = lastPose.plus(new Pose2d(0, 0, turnAnglePowershot12));
            //Turn to Powershot3
            lastPose = lastPose.plus(new Pose2d(0, 0, turnAnglePowershot23));
        }


        /*trajSafePositionBeforeWobbleTargetDrop = hzDrive.trajectoryBuilder(lastPose)
                .lineToSplineHeading(new Pose2d(46,22,Math.toRadians(-45)))
                //.lineToSplineHeading(new Pose2d(50,15,Math.toRadians(-45)))
                .build();
        lastPose = trajSafePositionBeforeWobbleTargetDrop.end();*/

        switch (targetZone){
            case A:
                trajSafePositionBeforeWobbleTargetDrop = hzDrive.trajectoryBuilder(lastPose)
                        .lineToSplineHeading(new Pose2d(46,22,Math.toRadians(-45)))
                        //.lineToSplineHeading(new Pose2d(50,15,Math.toRadians(-45)))
                        .build();
                lastPose = trajSafePositionBeforeWobbleTargetDrop.end();
                trajWobbleDropPosition = hzDrive.trajectoryBuilder(lastPose)
                        //.lineToSplineHeading(new Pose2d(23,41,Math.toRadians(-45)))
                        .lineToSplineHeading(new Pose2d(27,43,Math.toRadians(-45)))
                        .build();
                lastPose = trajWobbleDropPosition.end();
                /*trajPickRingsFromTargetMark = hzDrive.trajectoryBuilder(lastPose)
                        .lineToLinearHeading(new Pose2d(-5,36,Math.toRadians(-180)))
                        .build();
                lastPose = trajPickRingsFromTargetMark.end();*/
                trajSafePositionAfterWobbleTargetDrop = hzDrive.trajectoryBuilder(lastPose)
                        .lineToLinearHeading(new Pose2d(50,16,Math.toRadians(-45)))
                        .build();
                lastPose = trajSafePositionAfterWobbleTargetDrop.end();
                trajParkWithoutPickRing = hzDrive.trajectoryBuilder(lastPose)
                        .lineToSplineHeading(new Pose2d(13, 20, Math.toRadians(0)))
                        .build();
                lastPose = trajParkWithoutPickRing.end();
                break;

            case B:
                trajWobbleDropPosition = hzDrive.trajectoryBuilder(lastPose)
                        .lineToSplineHeading(new Pose2d(46,22,Math.toRadians(-45)))
                        .build();
                lastPose = trajWobbleDropPosition.end();
                if (hzAutoControl.pickRingFromTargetMarker == true) {
                    trajPickRingsFromTargetMark = hzDrive.trajectoryBuilder(lastPose)
                            //.lineToLinearHeading(new Pose2d(-5, 36, Math.toRadians(-180)))
                            .lineToLinearHeading(new Pose2d(-20,36,Math.toRadians(-180)))
                            .build();
                    lastPose = trajPickRingsFromTargetMark.end();
                    if (hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal == true) {
                        trajTurnToHighGoal = hzDrive.trajectoryBuilder(lastPose)
                                .lineToLinearHeading(new Pose2d(-10, 33, Math.toRadians(0)))
                                .build();
                        lastPose = trajTurnToHighGoal.end();
                        trajPark = hzDrive.trajectoryBuilder(lastPose)
                                .lineToSplineHeading(new Pose2d(12, 33, Math.toRadians(0)))
                                .build();
                        lastPose = trajPark.end();

                    } else {
                        trajSafePostionBeforePark = hzDrive.trajectoryBuilder(lastPose)
                                .lineToLinearHeading(new Pose2d(-50, 16, Math.toRadians(-90)))
                                .build();
                        lastPose = trajSafePostionBeforePark.end();

                        trajPark = hzDrive.trajectoryBuilder(lastPose)
                                .lineToSplineHeading(new Pose2d(12, 0, Math.toRadians(0)))
                                .build();
                        lastPose = trajPark.end();
                    }

                } else {
                    trajSafePositionAfterWobbleTargetDrop = hzDrive.trajectoryBuilder(lastPose)
                            .lineToSplineHeading(new Pose2d(50, 16, Math.toRadians(-45)))
                            .build();
                    lastPose = trajSafePositionAfterWobbleTargetDrop.end();

                    trajParkWithoutPickRing = hzDrive.trajectoryBuilder(lastPose)
                            .lineToSplineHeading(new Pose2d(13, 20, Math.toRadians(0)))
                            .build();
                    lastPose = trajParkWithoutPickRing.end();

                }
                break;

            case C:
                trajWobbleDropPosition = hzDrive.trajectoryBuilder(lastPose)
                        .lineToSplineHeading(new Pose2d(52,43,Math.toRadians(-90)))
                        .build();
                lastPose = trajWobbleDropPosition.end();
                if (hzAutoControl.pickRingFromTargetMarker == true) {
                    trajPickRingsFromTargetMark = hzDrive.trajectoryBuilder(lastPose)
                            //.lineToLinearHeading(new Pose2d(-5, 36, Math.toRadians(-180)))
                            //.lineToLinearHeading(new Pose2d(-12, 36, Math.toRadians(-180)))
                            .lineToLinearHeading(new Pose2d(-30, 36, Math.toRadians(-180)))
                            .build();
                    lastPose = trajPickRingsFromTargetMark.end();
                    if (hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal == true) {
                        trajTurnToHighGoal = hzDrive.trajectoryBuilder(lastPose)
                                .lineToLinearHeading(new Pose2d(-10, 33, Math.toRadians(0)))
                                .build();
                        lastPose = trajTurnToHighGoal.end();
                        trajPark = hzDrive.trajectoryBuilder(lastPose)
                                .lineToSplineHeading(new Pose2d(12, 33, Math.toRadians(0)))
                                .build();
                        lastPose = trajPark.end();

                    } else {
                        trajSafePostionBeforePark = hzDrive.trajectoryBuilder(lastPose)
                                .lineToLinearHeading(new Pose2d(-50, 16, Math.toRadians(-90)))
                                .build();
                        lastPose = trajSafePostionBeforePark.end();

                        trajPark = hzDrive.trajectoryBuilder(lastPose)
                                .lineToSplineHeading(new Pose2d(12, 0, Math.toRadians(0)))
                                .build();
                        lastPose = trajPark.end();
                    }

                } else {
                    trajSafePositionAfterWobbleTargetDrop = hzDrive.trajectoryBuilder(lastPose)
                            .lineToSplineHeading(new Pose2d(50, 16, Math.toRadians(-45)))
                            .build();
                    lastPose = trajSafePositionAfterWobbleTargetDrop.end();

                    trajParkWithoutPickRing = hzDrive.trajectoryBuilder(lastPose)
                            .lineToSplineHeading(new Pose2d(13, 20, Math.toRadians(0)))
                            .build();
                    lastPose = trajParkWithoutPickRing.end();

                }
                break;
        }
    }


    public void runAutoBlueInner(){

        switch(currentAutoStepState) {
            case START:
                //DEBUG
                telemetry.addData("currentAutoStepState : ", currentAutoStepState);
                telemetry.update();
                //hzWait(500);
                currentAutoStepState = AutoStepState.INITIATE;
                break;
            case INITIATE:
                //DEBUG
                telemetry.addData("currentAutoStepState : ", currentAutoStepState);
                telemetry.update();
                hzAutoControl.setMagazineToLaunch();
                if (hzAutoControl.autoLaunchAim == HzAutoControl.AutoLaunchAim.HIGHGOAL) {
                    hzAutoControl.setLaunchTargetHighGoal();
                    currentAutoStepState = AutoStepState.TRAJ_LAUNCH_RINGS_HIGHGOAL;
                } else {
                    hzAutoControl.setLaunchTargetPowerShot1();
                    currentAutoStepState = AutoStepState.TRAJ_LAUNCH_RING_POWERSHOT1;
                }

                //hzWait(500);
                break;

            case TRAJ_LAUNCH_RINGS_HIGHGOAL:
                //DEBUG
                telemetry.addData("currentAutoStepState : ", currentAutoStepState);
                telemetry.update();
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectory(trajTemp);
                    hzDrive.followTrajectoryAsync(trajLaunchRingsHighGoal);
                    currentAutoStepState = AutoStepState.LAUNCH_RINGS_HIGHGOAL;
                }
                //hzWait(500);
                break;

            case LAUNCH_RINGS_HIGHGOAL:
                //DEBUG
                telemetry.addData("currentAutoStepState : ", currentAutoStepState);
                telemetry.update();
                hzAutoControl.setMagazineToLaunch();
                hzAutoControl.setLaunchTargetHighGoal();
                if (!hzDrive.isBusy()) {
                    launch3RingsToHighGoal();
                    switch (targetZone){
                        case A :
                            currentAutoStepState = AutoStepState.TRAJ_SAFE_POSITION_BEFORE_WOBBLE_TARGET_DROP;
                            break;
                        case B :
                            currentAutoStepState = AutoStepState.TRAJ_WOBBLE_DROP_POSITION;
                            break;
                        case C :
                            currentAutoStepState = AutoStepState.TRAJ_WOBBLE_DROP_POSITION;
                            break;
                    }
                }
                //hzWait(500);
                break;

            case TRAJ_LAUNCH_RING_POWERSHOT1:
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectoryAsync(trajLaunchRingPowershot1);
                    currentAutoStepState = AutoStepState.LAUNCH_RINGS_POWERSHOT1;
                }
                break;

            case LAUNCH_RINGS_POWERSHOT1:
                hzAutoControl.setMagazineToLaunch();
                hzAutoControl.setLaunchTargetPowerShot1();
                if (!hzDrive.isBusy()) {
                    hzWait(500);
                    hzAutoControl.setRunLauncherTrue();
                    currentAutoStepState = AutoStepState.TRAJ_LAUNCH_RING_POWERSHOT2;
                }
                break;

            case TRAJ_LAUNCH_RING_POWERSHOT2:
                if (!hzDrive.isBusy()) {
                    hzDrive.turnAsync(turnAnglePowershot12);
                    currentAutoStepState = AutoStepState.LAUNCH_RINGS_POWERSHOT2;
                }
                break;
            case LAUNCH_RINGS_POWERSHOT2:
                hzAutoControl.setMagazineToLaunch();
                hzAutoControl.setLaunchTargetPowerShot2();
                if (!hzDrive.isBusy()) {
                    hzWait(500);
                    hzAutoControl.setRunLauncherTrue();
                    currentAutoStepState = AutoStepState.TRAJ_LAUNCH_RING_POWERSHOT3;
                }
                break;

            case TRAJ_LAUNCH_RING_POWERSHOT3:
                if (!hzDrive.isBusy()) {
                    hzDrive.turnAsync(turnAnglePowershot23);
                    currentAutoStepState = AutoStepState.LAUNCH_RINGS_POWERSHOT3;
                }
                break;

            case LAUNCH_RINGS_POWERSHOT3:
                hzAutoControl.setMagazineToLaunch();
                hzAutoControl.setLaunchTargetPowerShot3();
                if (!hzDrive.isBusy()) {
                    hzWait(500);
                    hzAutoControl.setRunLauncherTrue();
                    switch (targetZone){
                        case A :
                            currentAutoStepState = AutoStepState.TRAJ_SAFE_POSITION_BEFORE_WOBBLE_TARGET_DROP;
                            break;
                        case B :
                            currentAutoStepState = AutoStepState.TRAJ_WOBBLE_DROP_POSITION;
                            break;
                        case C :
                            currentAutoStepState = AutoStepState.TRAJ_WOBBLE_DROP_POSITION;
                            break;
                    }
                }
                break;

            case TRAJ_SAFE_POSITION_BEFORE_WOBBLE_TARGET_DROP:
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectoryAsync(trajSafePositionBeforeWobbleTargetDrop);
                    currentAutoStepState = AutoStepState.TRAJ_WOBBLE_DROP_POSITION;
                }
                break;

            case TRAJ_WOBBLE_DROP_POSITION:
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectoryAsync(trajWobbleDropPosition);
                    currentAutoStepState = AutoStepState.DROP_WOBBLE_GOAL_ON_TARGET;
                }
                break;

            case DROP_WOBBLE_GOAL_ON_TARGET:
                if (!hzDrive.isBusy()) {
                    dropWobbleGoalInTarget();
                    if (targetZone == HzGameField.TARGET_ZONE.A) {
                        currentAutoStepState = AutoStepState.TRAJ_SAFE_POSITION_AFTER_WOBBLE_TARGET_DROP;
                    } else {
                        if (hzAutoControl.pickRingFromTargetMarker == true) {
                            currentAutoStepState = AutoStepState.START_INTAKE;
                        } else {
                            currentAutoStepState = AutoStepState.TRAJ_SAFE_POSITION_AFTER_WOBBLE_TARGET_DROP;
                        }
                    }
                }
                break;

            case TRAJ_SAFE_POSITION_AFTER_WOBBLE_TARGET_DROP:
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectoryAsync(trajSafePositionAfterWobbleTargetDrop);
                    currentAutoStepState = AutoStepState.TRAJ_PARK_WITHOUT_PICK_RING;
                }
                break;

            case TRAJ_PARK_WITHOUT_PICK_RING:
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectoryAsync(trajParkWithoutPickRing);
                    currentAutoStepState = AutoStepState.END;
                }
                break;

            case START_INTAKE:
                if (!hzDrive.isBusy()) {
                    hzIntake.setIntakeReleaseOpen();
                    hzAutoControl.setIntakeStart();
                    currentAutoStepState = AutoStepState.TRAJ_PICK_RINGS_FROM_TARGET_MARK;
                }
                break;

            case TRAJ_PICK_RINGS_FROM_TARGET_MARK:
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectoryAsync(trajPickRingsFromTargetMark);
                    if (hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal == true){
                        currentAutoStepState = AutoStepState.TRAJ_TURN_TO_HIGH_GOAL;
                    } else {
                        currentAutoStepState = AutoStepState.TRAJ_SAFE_POSITION_BEFORE_PARK;
                    }
                }
                break;

            case TRAJ_TURN_TO_HIGH_GOAL:
                hzAutoControl.setMagazineToLaunch();
                hzAutoControl.setLaunchTargetHighGoal();
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectoryAsync(trajTurnToHighGoal);
                    currentAutoStepState = AutoStepState.LAUNCH_RINGS_HIGHGOAL_SECOND;
                }
                break;

            case LAUNCH_RINGS_HIGHGOAL_SECOND:
                hzAutoControl.setMagazineToLaunch();
                hzAutoControl.setLaunchTargetHighGoal();
                if (!hzDrive.isBusy()) {
                    launch3RingsToHighGoal();
                    currentAutoStepState = AutoStepState.TRAJ_PARK;
                }
                break;

            case TRAJ_SAFE_POSITION_BEFORE_PARK:
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectoryAsync(trajSafePostionBeforePark);
                    currentAutoStepState = AutoStepState.TRAJ_PARK;
                }
                break;

            case TRAJ_PARK:
                if (!hzDrive.isBusy()) {
                    hzDrive.followTrajectoryAsync(trajPark);
                    currentAutoStepState = AutoStepState.END;
                }
                break;

            case END:
                hzAutoControl.setIntakeStop();
                hzIntake.setIntakeReleaseOpen();
                if (!hzDrive.isBusy()) {
                    currentAutoStepState = AutoStepState.IDLE;
                }
                break;

            case IDLE:
                parked = true;
                break;
        }
    }


    public void hzWait(double time){
        ElapsedTime timer = new ElapsedTime(MILLISECONDS);
        timer.reset();
        while (!isStopRequested() && timer.time() < time){
            hzAutoControl.runAutoControl();
        }
    }

    public void launch3RingsToHighGoal(){
        hzAutoControl.setMagazineToLaunch();
        hzWait(750);
        hzAutoControl.setLaunchTargetHighGoal();
        hzAutoControl.setMagazineToLaunch();
        hzWait(350);
        hzAutoControl.setRunLauncherTrue();
        hzAutoControl.setMagazineToLaunch();
        hzWait(350);
        hzAutoControl.setRunLauncherTrue();
        hzAutoControl.setMagazineToLaunch();
        hzWait(350);
        hzAutoControl.setRunLauncherTrue();
        hzAutoControl.setMagazineToLaunch();
        hzWait(350);

        hzAutoControl.setLaunchTargetOff();
        hzAutoControl.setMagazineToCollect();
    }

    public void launch3RingsToPowerShots(){

        hzAutoControl.setLaunchTargetPowerShot1();
        hzAutoControl.setMagazineToLaunch();
        hzWait(350);
        hzAutoControl.setRunLauncherTrue();

        hzDrive.turnAsync(turnAnglePowershot12);
        if (!hzDrive.isBusy()) {
            hzWait(500);
            hzAutoControl.setRunLauncherTrue();
        }

        hzDrive.turnAsync(turnAnglePowershot23);
        //hzAutoControl.setMagazineToLaunch();
        //hzAutoControl.setLaunchTargetPowerShot1();
        if (!hzDrive.isBusy()) {
            hzWait(500);
            hzAutoControl.setRunLauncherTrue();
        }

        hzAutoControl.setLaunchTargetOff();
        hzAutoControl.setMagazineToCollect();

    }

    public void dropWobbleGoalInTarget(){
        hzAutoControl.setMoveArmDropWobbleAutonoumous();
        hzWait(1000);
        hzAutoControl.runOpenGrip();
        hzWait(300);
        hzAutoControl.setMoveArmHoldUpWobbleRing();
        hzAutoControl.setMoveArmParked();
    }

    public void selectGamePlan(){
        telemetry.setAutoClear(true);
        telemetry.addData("Compile time : ", "4:11 :: 1/14");

        //***** Select Alliance ******

        HzGameField.playingAlliance = HzGameField.PLAYING_ALLIANCE.BLUE_ALLIANCE;
        startPose = HzGameField.BLUE_INNER_START_LINE;
        activeWebcam = HzVuforia.ACTIVE_WEBCAM.RIGHT;

        while (!isStopRequested()) {
            telemetry.addData("Playing Alliance :", HzGameField.playingAlliance);
            telemetry.addData("startPose : ", startPose);
            telemetry.addData("activeWebcam : ",activeWebcam);
            telemetry.addLine();
            telemetry.addData("Please select launch target : ", "(B) for High Goal, (X) for Powershot");
            if (hzGamepad.getButtonBPress()) {
                hzAutoControl.autoLaunchAim = HzAutoControl.AutoLaunchAim.HIGHGOAL;
                telemetry.addData("hzAutoControl.autoLaunchAim : ", hzAutoControl.autoLaunchAim);
                break;
            }
            if (hzGamepad.getButtonXPress()) {
                hzAutoControl.autoLaunchAim = HzAutoControl.AutoLaunchAim.POWERSHOT;
                telemetry.addData("hzAutoControl.autoLaunchAim : ", hzAutoControl.autoLaunchAim);
                break;
            }
            telemetry.update();
            hzWait(200);
        }

        while (!isStopRequested()) {
            telemetry.addData("Playing Alliance :", HzGameField.playingAlliance);
            telemetry.addData("startPose : ", startPose);
            telemetry.addData("activeWebcam : ", activeWebcam);
            telemetry.addData("hzAutoControl.autoLaunchAim : ", hzAutoControl.autoLaunchAim);
            telemetry.addLine();
            telemetry.addData("Please select option for Picking rings from target markers and launching : ", "");
            telemetry.addData("    (A):","Park after Wobble Goal (No ring Pick)");
            telemetry.addData("    (Y):","Pick rings after Wobble Goal and park (No launching)");
            telemetry.addData("    (X):","Pick rings and launch after Wobble Goal, and park");
            if (hzGamepad.getButtonAPress()) {
                hzAutoControl.pickRingFromTargetMarker = false;
                hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal = false;
                telemetry.addData("hzAutoControl.pickRingFromTargetMarker : ", hzAutoControl.pickRingFromTargetMarker);
                telemetry.addData("hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal  : ", hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal);
                break;
            }
            if (hzGamepad.getButtonYPress()) {
                hzAutoControl.pickRingFromTargetMarker = true;
                hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal = false;
                telemetry.addData("hzAutoControl.pickRingFromTargetMarker : ", hzAutoControl.pickRingFromTargetMarker);
                telemetry.addData("hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal  : ", hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal);
                break;
            }
            if (hzGamepad.getButtonXPress()) {
                hzAutoControl.pickRingFromTargetMarker = true;
                hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal = true;
                telemetry.addData("hzAutoControl.pickRingFromTargetMarker : ", hzAutoControl.pickRingFromTargetMarker);
                telemetry.addData("hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal  : ", hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal);
                break;
            }
            telemetry.update();
        }

        telemetry.update();
        sleep(200);
    }


    /**
     * Method to add debug messages. Update as telemetry.addData.
     * Use public attributes or methods if needs to be called here.
     */
    public void printDebugMessages(){
        telemetry.setAutoClear(true);
        telemetry.addData("HzDEBUG_FLAG is : ", HzDEBUG_FLAG);
        telemetry.addData("autonomousStarted : ", autonomousStarted);
        telemetry.addData("buildAutoStepState : ", buildAutoStepState);

        telemetry.addData("Playing Alliance :", HzGameField.playingAlliance);
        telemetry.addData("startPose : ", startPose);
        telemetry.addData("activeWebcam : ", activeWebcam);
        telemetry.addData("hzAutoControl.autoLaunchAim : ", hzAutoControl.autoLaunchAim);
        telemetry.addData("hzAutoControl.pickRingFromTargetMarker : ", hzAutoControl.pickRingFromTargetMarker);
        telemetry.addData("hzAutoControl.launchRingsPicked  : ", hzAutoControl.launchRingsPickedFromTargetMarkerToHighGoal);
        telemetry.addData("autonomousStarted : ", autonomousStarted);
        telemetry.addData("currentAutoStepState : ", currentAutoStepState);
        telemetry.addData("lastPose : ", lastPose);

        //****** Drive debug ******
        telemetry.addData("Drive Mode : ", hzDrive.driveMode);
        telemetry.addData("PoseEstimate :", hzDrive.poseEstimate);
        telemetry.addData("HzGameField.currentPose",HzGameField.currentPose);

        //telemetry.addData("Visible Target : ", hzVuforia.visibleTargetName);
        telemetry.addData("hzVuforia.targetZoneDetected", hzVuforia.targetZoneDetected);
        telemetry.addData("targetZone :", targetZone);
        // Print pose to telemetry
        //telemetry.addData("PoseVuforia :",hzVuforia1.poseVuforia);

        //******* Magazine Debug ********
        switch (hzMagazine.getMagazinePosition()) {
            case AT_LAUNCH: {
                telemetry.addData("hzMagazine.getMagazinePosition(): ", "MAGAZINE_AT_LAUNCH");
                break;
            }
            case AT_COLLECT: {
                telemetry.addData("hzMagazine.getMagazinePosition():", "MAGAZINE_AT_COLLECT");
                break;
            }
            case AT_ERROR: {
                telemetry.addData("hzMagazine.getMagazinePosition():", "MAGAZINE_AT_ERROR");
                break;
            }
        }
        telemetry.addData("magazineLaunchTouchSensor.getState():", hzMagazine.magazineLaunchTouchSensor.isPressed());
        telemetry.addData("magazineCollectTouchSensor.getState():", hzMagazine.magazineCollectTouchSensor.isPressed());

        //********** Intake Debug *******
        //telemetry.addData("hzGamepad1.getDpad_downPress()", hzGamepad.getDpad_downPress());
        //telemetry.addData("hzGamepad1.getDpad_upPress()", hzGamepad.getDpad_upPress());
        //telemetry.addData("intakeMotor.isBusy()", hzIntake.intakeMotor.isBusy());
        switch (hzIntake.getIntakeState()){
            case RUNNING: {
                telemetry.addData("hzIntake.getIntakeState()", "INTAKE_MOTOR_RUNNING");
                break;
            }
            case STOPPED: {
                telemetry.addData("hzIntake.getIntakeState()", "INTAKE_MOTOR_STOPPED");
                break;
            }
            case REVERSING: {
                telemetry.addData("hzIntake.getIntakeState()", "INTAKE_MOTOR_REVERSING");
                break;
            }
        }

        //******* Launch Controller Debug ********
        telemetry.addData("hzLaunchController.launchMode : ", hzLaunchController.launchMode);
        telemetry.addData("hzLaunchController.launchReadiness : ", hzLaunchController.launchReadiness);
        telemetry.addData("hzLaunchController.launchActivation : ", hzLaunchController.launchActivation);
        telemetry.addData("hzLaunchController.lcTarget : ", hzLaunchController.lcTarget);
        telemetry.addData("hzLaunchController.lcTargetVector", hzLaunchController.lcTargetVector);
        telemetry.addData("hzLaunchController.distanceFromTarget : ", hzLaunchController.distanceFromTarget);
        telemetry.addData("hzLaunchController.lclaunchMotorPower : ", hzLaunchController.lclaunchMotorPower);
        telemetry.addData("hzDrive.drivePointToAlign : ", hzDrive.drivePointToAlign);

        //******* Launcher Debug *********
        //telemetry.addData("launcherFlyWheelMotor.isBusy()", hzLauncher.launcherFlyWheelMotor.isBusy());
        telemetry.addData("launcherRingPlungerServo.getPosition() : ", hzLauncher.launcherRingPlungerServo.getPosition());

        switch (hzLauncher.getLauncherState()){
            case RUNNING_FOR_TARGET:  {
                telemetry.addData("hzLauncher.getLauncherState()", "FLYWHEEL_RUNNING_FOR_TARGET");
                break;
            }
            case RUNNING_FOR_SUPPLY:  {
                telemetry.addData("hzLauncher.getLauncherState()", "FLYWHEEL_RUNNING_FOR_SUPPLY");
                break;
            }
            case STOPPED:  {
                telemetry.addData("hzLauncher.getLauncherState()", "FLYWHEEL_STOPPED");
                break;
            }
        }




        //***** Arm Debug ****
        //telemetry.addData("armMotor.getTargetPosition()", hzArm.armMotor.getTargetPosition());
        //telemetry.addData("armMotor.getCurrentPosition()", hzArm.armMotor.getCurrentPosition());

        switch (hzArm.getGripServoState()){
            case OPENED  : {
                telemetry.addData("hzArm.getGripServoState()", "OPENED");
                break;
            }
            case CLOSED: {
                telemetry.addData("hzArm.getGripServoState()", "CLOSED");
                break;
            }
        }

        //telemetry.addData("armMotor.getCurrentPosition()", hzArm.armMotor.getCurrentPosition());
        //telemetry.addData("armMotor.getTargetPosition()", hzArm.armMotor.getTargetPosition());

        //telemetry.addData("armGripServo.getCurrentPosition()", hzArm.armGripServo.getPosition());
        //telemetry.addData("hzGamepad.getLeftTriggerPress()", hzGamepad.getLeftTriggerPress());

        switch (hzArm.getCurrentArmPosition()){
            case PARKED: {
                telemetry.addData("hzArm.getCurrentArmPosition()", "PARKED");
                break;
            }
            case DROP_WOBBLE_RING: {
                telemetry.addData("hzArm.getCurrentArmPosition()", "DROP_WOBBLE_RING");
                break;
            }
            case HOLD_UP_WOBBLE_RING: {
                telemetry.addData("hzArm.getCurrentArmPosition()", "HOLD_UP_WOBBLE_RING");
                break;
            }
            case PICK_WOBBLE:{
                telemetry.addData("hzArm.getCurrentArmPosition()", "PICK_WOBBLE");
                break;
            }
            case PICK_RING: {
                telemetry.addData("hzArm.getCurrentArmPosition()", "PICK_RING");
                break;
            }
        }

        telemetry.update();

    }
}

/*
Turn command : drive.turn(Math.toRadians(ANGLE));

Blue Alliance - Inner position-Target A
    Start Pose : (TBD, 25, ~-70deg)
    Spline to (0,12,0)
    Launch ring to power shot 1,2,3, (automatic alignment)
    Spline to (12,36,-90)
    Drop wobble goal
    Spline to (24,24,0)

Blue Alliance - Inner position-Target B
    Start Pose : (TBD, 25, ~-70deg)
    Launch ring to power shot 1,2,3, (automatic alignment)
    spline to (12,36,180)
    Drop wobble goal
    Turn intake on
    Straight to (-24,36,180)
    Turn 180 deg
    Launch to high goal
    Move to (12,36,0)

Blue Alliance - Inner position-Target C - Option 1
    Start Pose : (TBD, 25, ~-70deg)
    Launch ring to power shot 1,2,3, (automatic alignment)
    Turn  to 145 deg
    Turn Intake on
    Straight to (-24,36,145)
    Turn to 0
    Launch to High goal x 3
    Spline to (40,40,-145)
    Drop Wobble goal
    Spline to (24,24,0)

Blue Alliance - Inner position-Target C - Option 2
    Start Pose : (TBD, 25, ~-70deg)
    Launch ring to power shot 1,2,3, (automatic alignment)
    Spline to (40,40,-145)
    Drop Wobble goal
    Turn Intake on
    Spline to (-24,36,180)
    Turn to 0
    Launch to High goal x 3
    Spline to (24,24,0)


 */
