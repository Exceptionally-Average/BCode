package lumbercheeseplayer;
import battlecode.common.*;
import sun.reflect.generics.tree.Tree;

public strictfp class RobotPlayer {
    static RobotController rc;
    static Direction startingEnemyDirection;
    static MapLocation[] startingEnemyPos = null;
    static MapLocation[] startingTeamPos = null;
    static int squadronNr;
    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        if (startingTeamPos == null) {
            startingEnemyPos = rc.getInitialArchonLocations(rc.getTeam().opponent()).clone();
            startingTeamPos = rc.getInitialArchonLocations(rc.getTeam()).clone();
        }

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
        }
    }

    static boolean metcorner = false;

    static void runArchon() throws GameActionException {
        Direction direction = Direction.getNorth();
        getMapSymmettry();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                /*
                Direction outOfCorner = findDirectionOutOfCorner();
                if (!metcorner && outOfCorner != null){
                    direction = outOfCorner;
                    metcorner = true;
                }
                if (rc.canMove(direction)) {
                    tryMove(direction);
                }
                */

                // Generate a random direction
                Direction dir = randomDirection();

                // Randomly attempt to build a gardener in this direction
                if (rc.canHireGardener(dir) && (rc.getRoundNum() <= 2 || rc.getTeamBullets() > RobotType.GARDENER.bulletCost + 10)) {
                    rc.hireGardener(dir);
                }

                // Move randomly
                tryMove(randomDirection());

                // Broadcast archon's location for other robots on the team to know
                MapLocation myLocation = rc.getLocation();


                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static void runGardener() throws GameActionException {
        int squadronMembersBuilt = 6;
        boolean squadronBuilder = false;

        if (rc.getRoundNum() <= 3){
            squadronBuilder = true;
        }
        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // Listen for home archon's location
                //int xPos = rc.readBroadcast(0);
                //int yPos = rc.readBroadcast(1);
                //MapLocation archonLoc = new MapLocation(xPos,yPos);

                // Generate a random direction
                Direction dir = randomDirection();

                if (squadronMembersBuilt == 6){
                    squadronMembersBuilt = 0;
                }

                if (rc.canBuildRobot(RobotType.LUMBERJACK, dir) && rc.isBuildReady() && squadronBuilder) {
                    rc.buildRobot(RobotType.LUMBERJACK, dir);
                    squadronMembersBuilt += 1;
                }

                if (squadronMembersBuilt == 0) {
                    // Move randomly
                    tryMove(randomDirection());
                }
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static void runSoldier() throws GameActionException {
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // If there are some...
                if (robots.length > 0) {
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(robots[0].location));
                    }
                }

                // Move randomly
                tryMove(randomDirection());


                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        boolean isCommander = commanderOrNot();

        if (isCommander) {
            //Start new squadron and notify that there is a new one added
            squadronNr = rc.readBroadcast(0) + 1;
            rc.broadcast(0, squadronNr + 1);
            commanderMove();
        }else{
            //Join the last squadron
            squadronNr = rc.readBroadcast(0) - 1;
            int readMsg = rc.readBroadcast(squadronNr);
            rc.broadcast(squadronNr, readMsg + 1);
            squadronMemberMove();
        }
    }

    static void commanderMove() throws GameActionException{

        Team enemy = rc.getTeam().opponent();
        boolean isGoingSomewhere = false;
        int spawnCounter = 0;
        int timesBeenToEnemySpawn = 0;

        MapLocation someSpawn = new MapLocation(0,0);
        Direction toSomeSpawn;
        MapLocation[] mapLocations = mergeArrays(startingEnemyPos,startingTeamPos);
        // The code you want your robot to perform every round should be in this loop
        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // If there aren't enough members to form a squadron wait
                if (rc.readBroadcast(squadronNr) >= 2) {
                    // See if there are any enemy robots within striking  range (distance 1 from lumberjack's radius)
                    RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                    // See if there are any allied robots within striking range (distance 1 from lumberjack's radius)
                    RobotInfo[] alliedRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());

                    if (robots.length > 0 && rc.getHealth() <= RobotType.LUMBERJACK.maxHealth * .25){
                        rc.broadcast(squadronNr,99999);
                    }

                    if (robots.length > 0 && !rc.hasAttacked()) {
                        RobotInfo target = robots[0];
                        for (RobotInfo bot : robots){
                            if (bot.getType() == RobotType.ARCHON){
                                target = bot;
                                break;
                            }else if (bot.getType() == RobotType.GARDENER){
                                target = bot;
                                break;
                            }
                        }
                        if (alliedRobots.length == 0) {
                            // Check if you can kill the enemy robot
                            if (RobotType.LUMBERJACK.attackPower >= target.getHealth()) ;
                            {
                                rc.broadcast(squadronNr, encodeMessage(Math.round(target.getLocation().x), Math.round(target.getLocation().y)));
                                rc.broadcast(squadronNr + 1, 0);
                            }

                            // Use strike() to hit all nearby robots!
                            rc.strike();
                            rc.broadcast(squadronNr, encodeMessage(Math.round(target.getLocation().x), Math.round(target.getLocation().y)));
                            rc.broadcast(squadronNr + 1, robots[0].getID());
                        } else {
                            Direction oppositeDirection = rc.getLocation().directionTo(alliedRobots[0].location).opposite();
                            if (rc.canMove(oppositeDirection, GameConstants.LUMBERJACK_STRIKE_RADIUS)) {
                                rc.move(oppositeDirection, GameConstants.LUMBERJACK_STRIKE_RADIUS);
                            } else {
                                rc.strike();
                            }
                        }
                        rc.broadcast(squadronNr, encodeMessage(Math.round(target.getLocation().x), Math.round(target.getLocation().y)));
                        rc.broadcast(squadronNr + 1,target.getID());
                    } else if (rc.senseNearbyRobots(-1, enemy).length > 0) {
                        // No close robots, so search for robots within sight radius
                        robots = rc.senseNearbyRobots(-1, enemy);

                        RobotInfo target = robots[0];
                        for (RobotInfo bot : robots){
                            if (bot.getType() == RobotType.ARCHON){
                                target = bot;
                                break;
                            }else if (bot.getType() == RobotType.GARDENER){
                                target = bot;
                                break;
                            }
                        }

                        // If there is a robot, move towards it
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = target.getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        rc.getRobotCount();
                        //tryMove(toEnemy);
                        tryBug(myLocation, enemyLocation);

                        rc.broadcast(squadronNr, encodeMessage(Math.round(target.getLocation().x), Math.round(target.getLocation().y)));
                        rc.broadcast(squadronNr + 1,target.getID());
                        //System.out.println("GOING TO FUCK SHIT UP");
                    } else if (isGoingSomewhere) {
                        toSomeSpawn = rc.getLocation().directionTo(someSpawn);
                        //tryMove(toSomeSpawn);
                        MapLocation myLocation = rc.getLocation();
                        tryBug(myLocation, someSpawn);
                        rc.broadcast(squadronNr, encodeMessage(Math.round(rc.getLocation().x), Math.round(rc.getLocation().y)));
                        rc.broadcast(squadronNr + 1, 0);
                    } else if (rc.onTheMap(rc.getLocation().add(startingEnemyDirection, RobotType.LUMBERJACK.sensorRadius - 0.1f)) && timesBeenToEnemySpawn != startingEnemyPos.length) {
                        //System.out.println("Going to their spawn");
                        //if you're not close to the end of the map and you can move go towards the enemy spawn
                        startingEnemyDirection = rc.getLocation().directionTo(startingEnemyPos[timesBeenToEnemySpawn]);
                        //tryMove(startingEnemyDirection);
                        MapLocation myLocation = rc.getLocation();
                        tryBug(myLocation, startingEnemyPos[0]);
                        int encodedmessage = encodeMessage(Math.round(rc.getLocation().x), Math.round(rc.getLocation().y));
                        rc.broadcast(squadronNr + 1, 0);
                        rc.broadcast(squadronNr, encodedmessage);
                        System.out.println("X: " + Math.round(rc.getLocation().x) + " y: " + Math.round(rc.getLocation().y));
                        System.out.println("ID: " + rc.readBroadcast(squadronNr + 1));
                        System.out.println(encodedmessage);
                    } else if (isStuckInCorner() && !isGoingSomewhere) {
                        if (spawnCounter == mapLocations.length) {
                            spawnCounter = 0;
                        }
                        someSpawn = mapLocations[spawnCounter];
                        toSomeSpawn = rc.getLocation().directionTo(someSpawn);
                        isGoingSomewhere = true;
                        //tryMove(toSomeSpawn);
                        MapLocation myLocation = rc.getLocation();
                        tryBug(myLocation, someSpawn);
                        spawnCounter += 1;
                        rc.broadcast(squadronNr, encodeMessage(Math.round(rc.getLocation().x), Math.round(rc.getLocation().y)));
                        rc.broadcast(squadronNr + 1, 0);
                    } else {
                        tryMove(randomDirection());
                        rc.broadcast(squadronNr, encodeMessage(Math.round(rc.getLocation().x), Math.round(rc.getLocation().y)));
                        rc.broadcast(squadronNr + 1, 0);
                        //System.out.println("Walking randomly");
                    }

                    if (timesBeenToEnemySpawn < startingTeamPos.length) {
                        if (rc.canSenseLocation(startingEnemyPos[timesBeenToEnemySpawn])) {
                            timesBeenToEnemySpawn += 1;
                        }
                    }

                    if (rc.canSenseLocation(someSpawn)) {
                        isGoingSomewhere = false;
                    }
                }
                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    static void squadronMemberMove() throws GameActionException{
        while (true) {
            try {
                int broadcast = rc.readBroadcast(squadronNr);
                if (rc.readBroadcast(squadronNr) != 99999) {
                    if (rc.readBroadcast(squadronNr) > 2) {
                        int[] decodedBroadcast = decodedMessage(broadcast).clone();
                        int broadcastedID = rc.readBroadcast(squadronNr + 1);
                        MapLocation location = new MapLocation(decodedBroadcast[0], decodedBroadcast[1]);
                        if (broadcastedID == 0) {
                            Direction toTarget = rc.getLocation().directionTo(location);
                            MapLocation offset1 = location.add(toTarget.rotateLeftDegrees(90), GameConstants.LUMBERJACK_STRIKE_RADIUS);
                            MapLocation offset2 = location.add(toTarget.rotateRightDegrees(90), GameConstants.LUMBERJACK_STRIKE_RADIUS);
                            if (rc.getLocation().distanceTo(offset1) > rc.getLocation().distanceTo(offset2)) {
                                location = offset2;
                            } else {
                                location = offset1;
                            }
                            //tryMove(rc.getLocation().directionTo(location));
                            MapLocation myLocation = rc.getLocation();
                            tryBug(myLocation, location);
                        } else {
                            // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                            RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam().opponent());
                            RobotInfo target = null;
                            for (RobotInfo robot : robots) {
                                if (robot.ID == broadcastedID) {
                                    target = robot;
                                    break;
                                }
                            }


                            Direction toTarget = rc.getLocation().directionTo(location);
                            MapLocation offset1 = location.add(toTarget.rotateLeftDegrees(90));
                            MapLocation offset2 = location.add(toTarget.rotateRightDegrees(90));
                            if (rc.getLocation().distanceTo(offset1) > rc.getLocation().distanceTo(offset2)) {
                                location = offset2;
                            } else {
                                location = offset1;
                            }

                            // See if there are any allied robots within striking range (distance 1 from lumberjack's radius)
                            RobotInfo[] alliedRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, rc.getTeam());

                            if (target != null && !rc.hasAttacked()) {
                                if (alliedRobots.length == 0) {
                                    // Use strike() to hit all nearby robots!
                                    rc.strike();
                                } else {
                                    Direction oppositeDirection = rc.getLocation().directionTo(alliedRobots[0].location).opposite();
                                    if (rc.canMove(oppositeDirection, GameConstants.LUMBERJACK_STRIKE_RADIUS)) {
                                        rc.move(oppositeDirection, GameConstants.LUMBERJACK_STRIKE_RADIUS);
                                    } else {
                                        rc.strike();
                                    }
                                }
                            } else if (target == null) {
                                //tryMove(rc.getLocation().directionTo(location));
                                MapLocation myLocation = rc.getLocation();
                                tryBug(myLocation, location);
                            }
                        }
                    }
                }else{
                    commanderMove();
                }
                Clock.yield();
            } catch (GameActionException x) {
                System.out.println("Squadron member exception");
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI/2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)x

        return (perpendicularDist <= rc.getType().bodyRadius);
    }

    static boolean isStuckInCorner() throws GameActionException{
        int index = 0;
        int nrDirections = 0;
        Direction north = Direction.getNorth();
        while (index < 360) {
            Direction direction = north.rotateLeftDegrees(index);
            if (!rc.onTheMap(rc.getLocation().add(direction, RobotType.LUMBERJACK.sensorRadius - 0.1f))) {
                nrDirections += 1;
            }

            //System.out.println("indx " + index + "");
            index += 90;
        }
        return nrDirections >= 2;
    }

    static void getMapSymmettry(){
        MapLocation spawnA = rc.getInitialArchonLocations(Team.A)[0];
        MapLocation spawnB = rc.getInitialArchonLocations(Team.B)[0];
        if (spawnB.y == spawnA.y){
            System.out.println("Vertical symmetry");
        }else if (spawnB.x == spawnA.x){
            System.out.println("Horizontal symmetry");
        }else if (spawnA.x != spawnB.x && spawnA.y != spawnB.y){
            System.out.println("Rotational symmetry");
        }
    }

    public static void tryBug(MapLocation currentLoc, MapLocation goal) throws GameActionException {

        int rageMeter = 0;
        Direction forward = currentLoc.directionTo(goal);
        Direction right = currentLoc.directionTo(goal).rotateRightDegrees(90);
        Direction left = currentLoc.directionTo(goal).rotateLeftDegrees(90);
        TreeInfo[] nearbyTrees = rc.senseNearbyTrees(RobotType.LUMBERJACK.sensorRadius);

        if (rc.canMove(goal)) {      //can move to goal and he's not angry
            rc.move(goal);
            rageMeter =- 4;
        } else if (!rc.canMove(goal)  && rageMeter <= 100) {       //cant move to goal...
            if (rc.canMove(right) && !rc.canMove(left)) {         //if he cant move left but he can move right
                rc.move(right);                                    //move right of goal
            } else if (rc.canMove(left) && !rc.canMove(right)) {       //if he cant move right but he can move left
                rc.move(left);                                      //move left of goal
            }
            rageMeter =+ 2;                                         //make him angry that he's not getting to goal
        } else if (!rc.canMove(goal)  && rageMeter >= 100){
            if(nearbyTrees.length > 3){
                MapLocation closestTree = new MapLocation(nearbyTrees[0].getLocation().x, nearbyTrees[0].getLocation().y);
                if(rc.canChop(closestTree)){
                    rc.chop(closestTree);
                }
            }
        }
    }

    static MapLocation[] mergeArrays(MapLocation[] locations1, MapLocation[] locations2){
        MapLocation[] mergedArray = new MapLocation[locations1.length * 2];
        int i = 0;
        for (MapLocation loc : locations1){
            mergedArray[i] = loc;
            i++;
        }
        for (MapLocation loc : locations2){
            mergedArray[i] = loc;
            i++;
        }
        return mergedArray;
    }

    static boolean commanderOrNot(){
        RobotInfo[] closeAllyRobots = rc.senseNearbyRobots(RobotType.LUMBERJACK.sensorRadius, rc.getTeam());
        boolean hascloseAlliedLumberjacks = false;

        for (RobotInfo info : closeAllyRobots){
            if (info.getType() == RobotType.LUMBERJACK && info.getHealth() == RobotType.LUMBERJACK.maxHealth){
                hascloseAlliedLumberjacks = true;
                break;
            }
        }

        if (!hascloseAlliedLumberjacks){
            return true;
        }
        return  false;
    }

    static int encodeMessage(int x, int y){
        int encodedMessage = (x * 1000) + y;
        return encodedMessage;
    }

    static int[] decodedMessage(int msg){
        int[] messageParts = new int[2];
        messageParts[1] = msg % 1000;
        msg = msg / 1000;
        messageParts[0] = msg;
        return messageParts;
    }

}