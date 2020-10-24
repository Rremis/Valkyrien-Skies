package org.valkyrienskies.mod.common.tileentity;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.block.BlockCaptainsChair;
import org.valkyrienskies.mod.common.piloting.ControllerInputType;
import org.valkyrienskies.mod.common.piloting.PilotControlsMessage;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import valkyrienwarfare.api.TransformType;

public class TileEntityCaptainsChair extends TileEntityPilotableImpl {

	private static HashMap<PhysicsObject, Timer> physicsTimer = new HashMap<>();
	
    @Override
    public void processControlMessage(PilotControlsMessage message, EntityPlayerMP sender) {
        IBlockState blockState = getWorld().getBlockState(getPos());
        if (blockState.getBlock() == ValkyrienSkiesMod.INSTANCE.captainsChair) {
            PhysicsObject physicsObject = getParentPhysicsEntity();
            if (physicsObject != null) {
                processCalculationsForControlMessageAndApplyCalculations(physicsObject, message,
                    blockState);
            }
        } else {
            setPilotEntity(null);
        }
    }

    @Override
    public ControllerInputType getControlInputType() {
        return ControllerInputType.CaptainsChair;
    }

    @Override
    public boolean setClientPilotingEntireShip() {
        return true;
    }
    
    @Override
    public final void onStartTileUsage() {
    	PhysicsObject po = getParentPhysicsEntity();
    	
    	if(physicsTimer.containsKey(po)) {
    		physicsTimer.get(po).cancel();
    		physicsTimer.remove(po);
    	}
    	po.setPhysicsEnabled(true);
    }
    
    @Override
    public final void onStopTileUsage() {
        // Sanity check, sometimes we can be piloting something that's been destroyed so there's nothing to change physics on.
        if (getParentPhysicsEntity() != null) {
        	PhysicsObject po = getParentPhysicsEntity();
        	
        	Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                	po.setPhysicsEnabled(false);
                	physicsTimer.remove(po);
                }
            }, 5*60*1000); // 60 seconds * 5
            physicsTimer.put(po, timer);
        }
    }

    private void processCalculationsForControlMessageAndApplyCalculations(
            PhysicsObject controlledShip, PilotControlsMessage message, IBlockState state) {
        BlockPos chairPosition = getPos();

        if (controlledShip.isShipAligningToGrid()) {
            return;
        }

        double pilotPitch = 0D;
        double pilotYaw = ((BlockCaptainsChair) state.getBlock()).getChairYaw(state, chairPosition);
        double pilotRoll = 0D;

        Matrix3d pilotRotationMatrix = new Matrix3d();

        pilotRotationMatrix.rotateXYZ(Math.toRadians(pilotPitch), Math.toRadians(pilotYaw), Math.toRadians(pilotRoll));

        Vector3d playerDirection = new Vector3d(0.2, 0, 0);

        pilotRotationMatrix.transform(playerDirection);

        Vector3d idealAngularDirection = new Vector3d();
        Vector3d idealLinearVelocity = new Vector3d();

        Vector3d shipUp = new Vector3d(0, 0.8, 0);
        Vector3d shipUpPosIdeal = new Vector3d(0, 0.8, 0);

        if (message.airshipForward_KeyDown) {
            idealLinearVelocity.add(playerDirection);
        }
        if (message.airshipBackward_KeyDown) {
            idealLinearVelocity.sub(playerDirection);
        }

        controlledShip.getShipTransformationManager().getCurrentTickTransform()
            .transformDirection(idealLinearVelocity, TransformType.SUBSPACE_TO_GLOBAL);
        controlledShip.getShipTransformationManager().getCurrentTickTransform()
            .transformDirection(shipUp, TransformType.SUBSPACE_TO_GLOBAL);

        double sidePitch = 0;
        if (message.airshipRight_KeyDown) {
            idealAngularDirection.sub(shipUp);
            sidePitch -= 1D;
        }
        if (message.airshipLeft_KeyDown) {
            idealAngularDirection.add(shipUp);
            sidePitch += 1D;
        }

        Vector3d sidesRotationAxis = new Vector3d(playerDirection);
        controlledShip.getShipTransformationManager().getCurrentTickTransform()
            .transformDirection(sidesRotationAxis, TransformType.SUBSPACE_TO_GLOBAL);

        AxisAngle4d rotationSidesTransform = new AxisAngle4d(Math.toRadians(sidePitch), sidesRotationAxis.x, sidesRotationAxis.y,
                sidesRotationAxis.z);

        rotationSidesTransform.transform(shipUpPosIdeal);

        idealAngularDirection.mul(2);
        // The vector that points in the direction of the normal of the plane that
        // contains shipUp and shipUpPos. This is our axis of rotation.
        Vector3d shipUpRotationVector = shipUp.cross(shipUpPosIdeal, new Vector3d());
        // This isnt quite right, but it handles the cases quite well.
        double shipUpTheta = shipUp.angle(shipUpPosIdeal) + Math.PI;
        shipUpRotationVector.mul(shipUpTheta);

        idealAngularDirection.add(shipUpRotationVector);
        idealLinearVelocity.mul(20);

        // Move the ship faster if the player holds the sprint key.
        if (message.airshipSprinting) {
            idealLinearVelocity.mul(2);
        }

        double lerpFactor = .2;
        Vector3d linearMomentumDif = controlledShip.getPhysicsCalculations().getLinearVelocity().sub(idealLinearVelocity, new Vector3d());

        Vector3d angularVelocityDif = controlledShip.getPhysicsCalculations().getAngularVelocity().sub(idealAngularDirection, new Vector3d());

        linearMomentumDif.mul(lerpFactor);
        angularVelocityDif.mul(lerpFactor);

        controlledShip.getPhysicsCalculations().getLinearVelocity().sub(linearMomentumDif);
        controlledShip.getPhysicsCalculations().getAngularVelocity().sub(angularVelocityDif);
    }

}
