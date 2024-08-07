package org.firstinspires.ftc.teamcode.SampleEducationalPrograms.advanced.PIDToPoint;

import static org.firstinspires.ftc.teamcode.SampleEducationalPrograms.advanced.MathUtils.AngleWrap.angleWrap;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.teamcode.SampleEducationalPrograms.advanced.DriveEncoderLocalizer;
import org.firstinspires.ftc.teamcode.SampleEducationalPrograms.advanced.MathUtils.Pose2d;
import org.firstinspires.ftc.teamcode.SampleEducationalPrograms.advanced.MathUtils.Vector2D;
import org.firstinspires.ftc.teamcode.SampleEducationalPrograms.util.Drivetrain;
import org.firstinspires.ftc.teamcode.SampleEducationalPrograms.util.PIDController;

import java.util.ArrayList;
@Autonomous(name="TestPID", group="advanced")
public class TestPID extends LinearOpMode{
    private enum otherState {
        GO_TO_POINT,
        DRIVE,
        FINAL_ADJUSTMENT,
        BRAKE,
        WAIT_AT_POINT,
        IDLE
    }

    public otherState otherState;
    private DriveEncoderLocalizer localizer;
    private Drivetrain drive;
    private double xError;
    private double yError;
    private double turnError;
    private ArrayList<Pose2d> path;
    private int currentPoint;
    private otherState lastotherState;

    /**
     * OpModes must override the abstract runOpMode() method.
     */
    @Override
    public void runOpMode() throws InterruptedException {
        initialize();
        waitForStart();

        while (opModeIsActive() && !isStopRequested()) {
            Pose2d currentPose = localizer.getPoseEstimate();
            telemetry.addLine(currentPose.toString());
            switch (otherState) {
                case GO_TO_POINT:
                    if (currentPoint < path.size()) {
                        goToPoint(path.get(currentPoint));
                        if (atPoint() && currentPoint == path.size() - 1) {
                            otherState = otherState.FINAL_ADJUSTMENT;
                            System.out.println("xError: " + xError);
                            System.out.println("yError: " + yError);
                            System.out.println("Heading Error: " + turnError);
                            otherState = otherState.BRAKE;
                        } else if (atPoint()){
                            currentPoint++;
                            resetIntegrals();
                        }
                    } else {
                        otherState = otherState.BRAKE;
                    }
                    break;
                case FINAL_ADJUSTMENT:
                    calculateErrors(path.get(currentPoint));
                    finalAdjustment();
                    if (atPoint()) {
                        otherState = otherState.BRAKE;
                        System.out.println("xError: " + xError);
                        System.out.println("yError: " + yError);
                        System.out.println("Heading Error: " + turnError);
                    }
                    break;
                case BRAKE:
                    drive.setPower(0);
                    otherState = otherState.WAIT_AT_POINT;
                    break;
                case WAIT_AT_POINT:
                    if (!atPointThresholds(1.5, 1.5, 5)) {
                        resetIntegrals();
                        otherState = otherState.GO_TO_POINT;
                    }
                    break;
                case DRIVE:
                    if (gamepad1.x) {
                        drive.imu.resetYaw();
                    }
                    //drive(gamepad1.left_stick_x, -gamepad1.left_stick_y, gamepad1.right_stick_x);
                    applyKinematics(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);
                    break;
                case IDLE:
                    break;
            }

            if (gamepad1.a && otherState != otherState.DRIVE) {
                lastotherState = otherState;
                otherState = otherState.DRIVE;
            } else if (gamepad1.b) {
                otherState = lastotherState;
            }

            telemetry.addData("xError: ", xError);
            telemetry.addData("yError: ", yError);
            telemetry.addData("headingError: ", turnError);
            telemetry.addData("Current otherState: ", otherState);
            telemetry.addData("Current Point: ", (currentPoint < path.size()) ? "\n" + path.get(currentPoint) : "No more points");
            telemetry.update();
            localizer.update();
        }
    }

    public void initialize() throws InterruptedException {
        drive = new Drivetrain(hardwareMap, telemetry);
        localizer = new DriveEncoderLocalizer(drive.frontLeft, drive.frontRight, drive.backLeft, drive.backRight, drive.imu);
        otherState = otherState.GO_TO_POINT;
        path = new ArrayList<>();
        currentPoint = 0;
        Pose2d turn = new Pose2d(0, 0, Math.toRadians(20));
        Pose2d point1 = new Pose2d(30, 10, Math.toRadians(-45));
        Pose2d point2 = new Pose2d(20, -35, Math.toRadians(-180));
        Pose2d point3 = new Pose2d(40, 45, Math.toRadians(130));
        path.add(turn);
        path.add(point1);
        //path.add(point2);
        //path.add(point3);
    }


    // Non PID implementation of a movement to a point
    public void goToPosition(double targetX, double targetY, double targetHeading, double movementSpeed) {
        Pose2d currentPose = localizer.getPoseEstimate();
        double currentX = currentPose.getX();
        double currentY = currentPose.getY();
        double heading = currentPose.getHeading();

        double distance = Math.sqrt(Math.pow(targetX-currentX, 2) + Math.pow(targetY-currentY, 2));

        double absoluteAngleToTarget = Math.atan2(targetY-currentY, targetX-currentX);
        double relativeAngleToPoint = angleWrap(absoluteAngleToTarget - (heading - Math.toRadians(90)));

        double relativeX = Math.cos(relativeAngleToPoint) * distance;
        double relativeY = Math.sin(relativeAngleToPoint) * distance;

        double movementXPower = relativeX / (Math.abs(relativeX) + Math.abs(relativeY));
        double movementYPower = relativeY / (Math.abs(relativeX) + Math.abs(relativeY));

        double relativeTurnAngle = relativeAngleToPoint - Math.toRadians(180) + targetHeading;

        double movementTurn = Range.clip(relativeTurnAngle / Math.toRadians(30), -1, 1);
        if (distance < 10) {
            movementTurn = 0;
        }
        applyKinematics(movementXPower * movementSpeed, movementYPower * movementSpeed, movementTurn * movementSpeed);
    }


    // These PIDS should be more aggressive than the final pids

    public static PIDController xPID = new PIDController(0.04,0.0,0.003);
    public static PIDController yPID = new PIDController(0.125,0.0,0.175);
    public static PIDController turnPID = new PIDController(0.25,0.0,0.01);

    public static double xThreshold = 0.75;
    public static double yThreshold = 0.75;
    public static double turnThreshold = 3.0;

    // PID implementation of it
    public void goToPoint(Pose2d target) {
        Pose2d currentPose = localizer.getPoseEstimate();
        calculateErrors(target);
        turnError = angleWrap(turnError);

        double forward = Math.abs(xError) > xThreshold / 2 ? xPID.update(xError, -maxPower, maxPower) : 0;
        double strafe = Math.abs(yError) > yThreshold / 2 ? yPID.update(yError, -maxPower, maxPower) : 0;

        double turn = Math.abs(turnError) > Math.toRadians(turnThreshold) / 2 ? -turnPID.update(turnError, -maxPower, maxPower) : 0;

        double x_rotated = forward * Math.cos(-currentPose.getHeading()) - strafe * Math.sin(-currentPose.getHeading());
        double y_rotated = forward * Math.sin(-currentPose.getHeading()) + strafe * Math.cos(-currentPose.getHeading());

        applyKinematics(x_rotated, y_rotated, turn, true);
    }

    public void applyKinematics(double strafeSpeed, double forwardSpeed, double turnSpeed, boolean alt) {

        Vector2D input = new Vector2D(strafeSpeed, forwardSpeed);
        input = Vector2D.rotate(input, -localizer.getPoseEstimate().getHeading());

        strafeSpeed = Range.clip(input.getX(), -1, 1);
        forwardSpeed = Range.clip(input.getY(), -1, 1);
        turnSpeed = Range.clip(turnSpeed, -1, 1);

        double[] wheelSpeeds = new double[4];
        // FL, FR, BL, BR
        wheelSpeeds[0] = forwardSpeed + strafeSpeed + turnSpeed;
        wheelSpeeds[1] = forwardSpeed - strafeSpeed - turnSpeed;
        wheelSpeeds[2] = forwardSpeed - strafeSpeed + turnSpeed;
        wheelSpeeds[3] = forwardSpeed + strafeSpeed - turnSpeed;

        double voltage = hardwareMap.voltageSensor.iterator().next().getVoltage();
        double correction = 12 / voltage;
        for (int i = 0; i < wheelSpeeds.length; i++) {
            wheelSpeeds[i] = Math.abs(wheelSpeeds[i]) < 0.01 ?
                    wheelSpeeds[i] * correction : (wheelSpeeds[i] + Math.signum(wheelSpeeds[i] * 0.085) * correction);
        }

        double max = 1;
        for (double wheelSpeed : wheelSpeeds) max = Math.max(max, Math.abs(wheelSpeed));
        if (max > 1) {
            for (int i = 0; i < wheelSpeeds.length; i++) {
                wheelSpeeds[i] /= max;
            }
        }

        drive.frontLeft.setPower(wheelSpeeds[0]);
        drive.frontRight.setPower(wheelSpeeds[1]);
        drive.backLeft.setPower(wheelSpeeds[2]);
        drive.backRight.setPower(wheelSpeeds[3]);
    }

    public void applyKinematics(double x, double y, double turn) {
        double rotX = x;
        double rotY = y;
        Pose2d currentPose = localizer.getPoseEstimate();
        double botHeading = currentPose.getHeading();
        rotX = x * Math.cos(botHeading) - y * Math.sin(botHeading);
        rotY = x * Math.sin(botHeading) + y * Math.cos(botHeading);


        // Denominator is the largest motor power (absolute value) or 1
        // This ensures all the powers maintain the same ratio,
        // but only if at least one is out of the range [-1, 1]
        double denominator = Math.max(Math.abs(rotY) + Math.abs(rotX) + Math.abs(turn), 1);
        double frontLeftPower = (rotX + rotY + turn) / denominator;
        double backLeftPower = (rotX - rotY + turn) / denominator;
        double frontRightPower = (rotX - rotY - turn) / denominator;
        double backRightPower = (rotX + rotY - turn) / denominator;
        drive.frontLeft.setPower(frontLeftPower);
        drive.frontRight.setPower(frontRightPower);
        drive.backLeft.setPower(backLeftPower);
        drive.backRight.setPower(backRightPower);
    }


    public static PIDController finalXPID = new PIDController(0.035, 0.0,0.0);
    public static PIDController finalYPID = new PIDController(0.1, 0.0,0.0);
    public static PIDController finalTurnPID = new PIDController(0.01, 0.0,0.0);

    public static double finalXThreshold = 0.4;
    public static double finalYThreshold = 0.4;
    public static double finalTurnThreshold = 2.0;

    double maxPower = 1;

    public void finalAdjustment() {

        turnError = angleWrap(turnError);
        double forward = Math.abs(xError) > finalXThreshold / 2 ? finalXPID.update(xError, -maxPower, maxPower) : 0;
        double strafe = Math.abs(yError) > finalYThreshold / 2 ? -finalYPID.update(yError, -maxPower, maxPower) : 0;
        double turn = Math.abs(turnError) > Math.toRadians(finalTurnThreshold) / 2 ? -finalTurnPID.update(turnError, -maxPower, maxPower) : 0;

        applyKinematics(forward, strafe, turn);
    }

    public void calculateErrors(Pose2d target) {
        Pose2d currentPose = localizer.getPoseEstimate();
        xError = target.getX() - currentPose.getX();
        yError = target.getY() - currentPose.getY();
        turnError = target.getHeading() - currentPose.getHeading();
    }

    public boolean atPoint() {
        if (otherState == otherState.FINAL_ADJUSTMENT) {
            return Math.abs(xError) < finalXThreshold && Math.abs(yError) < finalYThreshold && Math.abs(turnError) < Math.toRadians(finalTurnThreshold);
        }
        return Math.abs(xError) < xThreshold && Math.abs(yError) < yThreshold && Math.abs(turnError) < Math.toRadians(turnThreshold);
    }

    public boolean atPointThresholds (double xThresh, double yThresh, double headingThresh) {
        return Math.abs(xError) < xThresh && Math.abs(yError) < yThresh && Math.abs(turnError) < Math.toRadians(headingThresh);
    }

    public void resetIntegrals() {
        xPID.resetIntegral();
        yPID.resetIntegral();
        turnPID.resetIntegral();
        finalXPID.resetIntegral();
        finalYPID.resetIntegral();
        finalTurnPID.resetIntegral();
    }

    public void drive(double x, double y, double turn) {
        double backLeftPower, frontLeftPower, frontRightPower, backRightPower;
        double power, theta, botHeading;
        botHeading = drive.imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS);


        double rotX = x * Math.cos(-botHeading) - y * Math.sin(-botHeading);
        double rotY = x * Math.sin(-botHeading) + y * Math.cos(-botHeading);
        rotX *= 1.1;
        double denominator = Math.max(Math.abs(rotY) + Math.abs(rotX) + Math.abs(turn), 1);

        frontLeftPower = (rotY + rotX + turn) / denominator;
        backLeftPower = (rotY - rotX + turn) / denominator;
        frontRightPower = (rotY - rotX - turn) / denominator;
        backRightPower = (rotY + rotX - turn) / denominator;
        drive.backLeft.setPower(backLeftPower);
        drive.backRight.setPower(backRightPower);
        drive.frontLeft.setPower(frontLeftPower);
        drive.frontRight.setPower(frontRightPower);
    }

}
