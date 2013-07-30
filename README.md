HybridVM
========

A Java implementation of the Lua scripting language, with proper coroutine handling.

The documentation is lacking at the moment, but I am going to do that sometime soon.


The engine folder contains the engine core, the asm folder contains helpers to
generate lua to Java bridges, eliminating the need to write inner classes, or
extra code to get existing classes work in the engine.

To compile the engine with class generation support, you will need to have Java ASM
on the classpath. ( The asm, and asm-commons jars. Grab them from: http://asm.ow2.org/ )

The target platform is Java 1.6, hence the excessive use of generics.

ASM Access Generation
=======
BE WARNED, if you wrap a List<T>, Map<K, V>, or other generic based object with MetaFactory,
it might cause unexpected behavior, as java generics are lost during compile time, there is
a way for scripts to bypass the typecheck, and cause unexpected ClassCastExceptions!

(This behavior is the same with reflection, therefore no other engines can protect you from such 'attacks')

It is advised to have custom classes without generic parameters.

License
=======

Hybrid is distributed under the MIT license, the same as standard Lua. 
(Means you can do almost whatever you want to do with it.)

However, I would really appreciate bug reports, fixes, pull requests, or ideas.

If you have any questions, or suggestions about Hybrid, feel free to contact me!
