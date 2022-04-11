Overview
=========

Tigase Database Migrator is component allowing migration of different types of data from various XMPP servers. Currently supports following servers and data:

-  ``ejabberd``

   -  User data (authentication, roster)

   -  MUC (multi user chat)

   -  PubSub

Usage
======

Migrator is a command-line utility. Main class: ``tigase.db.converter.Converter``, if executed without any parameters will display help with all parameters explained:

::

   $ java -cp jars/*:. tigase.db.converter.Converter [options]

Converter options
-------------------

Following options are supported

-  ``-I`` or ``--interactive`` (**optional**) - Enable interactive mode, which will result in prompting for missing parameters

-  ``-R value`` or ``--repository-class=value`` - allows specifying ``DataRepository`` implementation used for reading data from source; must implement tigase.db.DataSource (default: ``tigase.db.jdbc.DataRepositoryImpl``)

-  ``-S value`` or ``--source-uri=value`` - URI of the source do the data: ``jdbc:xxxx://<host>/<database>…``

-  ``-T value`` or ``--server-type=value`` - type of the server from which import will be performed, possible values: [ejabberd, ejabberd_new]

-  ``-D value`` or ``--destination-uri=value`` - URI of the destination for the data: ``jdbc:xxxx://<host>/<database>…``

-  ``-C value`` or ``--components=value`` (**optional**) - additional component beans names that should be activated

-  ``-H value`` or ``--virtual-host=value`` - allows specifying Virtual-host / domain name used by source installation (for example in case of old ejabberd installations)
