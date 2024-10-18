## SwitchRoles test: ##

### Test Summary: ###
1. Create and initialize N threads
2. Starts executing each thread at an interval of 10ms
3. If the thread number is even it:
    * Reads the contents of ReadFile.txt
    * Verifies that the data read is valid
4. Else, it:
   * Writes the thread number to WriteFile.txt

### Purpose: ###
* Stress test the Switch Roles logic by creating a multithreaded program.
* Number of threads are relatively high.
* At any point, there exist some threads that are initialized but have not started.

### Execution Steps: ###
1. Follow the _Initial Setup_ steps from `<repo>/README.md` to build and instrument JMVX.
2. Instrument SwitchRolesTest: <br>
`./<repo>/scripts/instrument-switch-roles-test.sh`
3. Start the Coordinator:<br>
`./<repo>/artifact/experiments/run-coordinator.sh`
4. In another terminal, start the test as Leader:<br>
`./<repo>/artifact/experiments/jmvx-test/run-switch-roles.sh Leader`
5. Similarly, in yet another terminal, start the test as Follower:<br>
`./<repo>/artifact/experiments/jmvx-test/run-switch-roles.sh Follower`
6. At any point in the execution, to switch roles between Leader and Follower:
   1. Go to the Coordinator
   2. When it indicates "_Role Switching Ready_", press `Return`.
7. If the operation was successful, both Leader and Follower should resume executing normally.
