# GemFire Delta Counter

Delta original concept was send only what is changed to the server.   This would allow very minimal amount of data to be transferred between hosts.  Then in GemFire 10 the team extended delta to be shared across a wide area network (WAN) connection.

While cool, we can take advantage of how delta works and have the delta be applied as an idempotent message to be applied like additions or subtraction from a counter.

For our delta counter to work we need to maintain state of counter and any changes that have been accumulating since we last stored the value in GemFire.   When storing the `DeltaCounter` in GemFire the `hasDelta` is checked to see if we send the current value (`false`) or the accumulator (`true`).

If we send the current value (`false`) the value that is stored is overwritten.

## How to Use

There are two methods of use - directly from a client or via a function.   The differance in the two approaches come from needing to check the value before addition or not.   Checking the value would require a `get` `doSomething` and finally storing.   If we did this get from a client perspective there will be two network hops - first from the `get` and the second from the `put` of the delta.

### Function use case

If we did this in a function there will be only one network call - there by increasing performance substantially due to the reduction of network overhead.  To ensure that we don't send the code to the wrong host we use a `optimizeForWrite` function and use a partitioned region.

`optimizeForWrite` signals to GemFire that this function when utilized with a `filter` it will send the function invocation to the host containing the data.   In the case of a Partitioned Region it will execute the function on the primary data host.   Making the read and write all local.

###  Function Code

I have attempted to make the code as simple as possible to use.   There is one main caveat to using this code is that we need to initialize the `DeltaCounter`  BEFORE invoking the function.   This has todo with a performance optimization for now the code has a race if we don't initialize before.

#### Initialize the Counter: 

`DeltaCounter` by the name of `name` which is stored in the accumulator region.
```java
accumulatorRegion.put("name", new DeltaCounter());
```

##### Call the Function

Increment the `DeltaCounter` which is named `name` by 1.   If the counter is greater than 400000 reject the increment.   The new value will be returned.
```java
int deltaCounter = DeltaCounterFunction.increment(accumulatorRegion, "name", 1, 400000);
```
### Client use case

In some instances all we care about is sending the commands to alter state, and it is up to some other process in an event driven architecture to perform any work.   Here we can just create an instance of our `DeltaCounter` apply any changes and do a `put` performing the same single network hop.

