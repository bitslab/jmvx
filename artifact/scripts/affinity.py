#!/usr/bin/python3
import subprocess
import sys
import os

#dirty hack
cores_per_socket = 0

def numa():
    # This function returns confirms that there are more than one CPU sockets
    # since lscpu always shows NUMA even though there is only one CPU socket
    global cores_per_socket
    command = """lscpu | grep "Core(s) per socket" | awk '{print $4}'"""
    cores_per_socket = int(subprocess.check_output(command, shell=True, stdin=subprocess.PIPE, stderr=subprocess.PIPE)[:-1]) - 1
    command = "lscpu | awk 'NF==4 && /NUMA/ { print $4 }'"
    result = subprocess.check_output(command, shell=True, stdin=subprocess.PIPE, stderr=subprocess.PIPE)
    if result is None or result == b'':
        return [(0, cores_per_socket)]
    #NOTE: this breaks if the cores per socket differs between sockets
    #but we are going with it for now
    cores = []
    for line in result.split(b'\n'):
        if(len(line) == 0):
            continue
        start_core = int(line[: line.find(b'-')])
        cores.append((start_core, start_core + cores_per_socket))
    return cores #[start_core, start_core + cores_per_socket]

def taskset(cpus):
    half = (cpus[1] - cpus[0]) // 2
    quater = half//2
    #first half of cpus in a socket
    leader = f'"taskset -c {cpus[0]}-{cpus[0] + half}"'
    #second half of cpus in socket
    follower = f'"taskset -c {cpus[1] - half}-{cpus[1]}"'
    #all cpus in socket
    coord = f'"taskset -c {cpus[0]}-{cpus[1]}"'
    #distributes loads among leader/follower in lieu of a second node
    #this is a fallback for non numa systems
    client = f'"taskset -c {cpus[0]+quater}-{cpus[1]-quater}"'
    return (leader, follower, coord, client)

def main():
    global core_per_socket #set in numa() func
    sockets = numa()
    print(sockets)
    (leader, follower, coord, client) = taskset(sockets[0])
    #If the machine has additional sockets, have the client use one!
    if(len(sockets) > 1):
        (client, _, _, _) = taskset(sockets[1])
    #let's us pin threads to cores 1 to 1 (or close enough, java will have a few extra in there)
    #add 1 b/c taskset ranges are inclusive
    threads = (cores_per_socket//2) + 1
    vars = [leader, follower, coord, client, threads]
    names = ["TASKSET_LEADER", "TASKSET_FOLLOWER", "TASKSET_COORD", "TASKSET_CLIENT", "THREADS"]

    for name, var in zip(names, vars):
        print(name, '=',  var)

    if len(sys.argv) == 2 and sys.argv[1] == "export": # Create file
        filepath = 'scripts/docker-specific.sh' # Default for docker
        if os.getenv("MVX_CONTAINER") == None: # For host machine
            filepath = "scripts/machine-specific.sh"
        try:
            with open(filepath, 'a') as f: # Append exports to .bashrc to automatically save variables for every bash instance
                for name, var in zip(names, vars):
                    f.write(f'export {name}={var}\n')
        except Exception as e:
            print("Exception:", e)


if __name__ == "__main__":
    main()
