Model Phases
============

Model creation goes through several phases. These phases are local to the current Thread and thus, thus all submodels share the same phase even if created by distinct calls to `create` methods.

# Creation

The creation phase is started by the first call to any creation method in a thread. It consists of the actual instantiation of the model root as well as calling its apply methods.

