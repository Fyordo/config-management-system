"""CMS Python SDK — Config Management System client library.

Reads initial configuration from a JSON file and keeps string properties up to date
by listening for binary update events on a UNIX domain socket sent by the CMS
agent (see ``agent/AGENT_CONTRACT.MD``).

Quick start::

    from cms import PropertyManager

    pm = PropertyManager(
        config_file_path="/etc/myapp/application.json",
        unix_socket_path="/run/cms/cms.sock",
    )

    pm.add_update_callback("feature.flag", lambda key, old, new: print(key, old, "->", new))
    pm.init()

    value = pm.get("feature.flag")

The ``unix_socket_path`` argument may be omitted if the ``CMS_UNIX_SOCKET_PATH``
environment variable is set.
"""

from .property_manager import PropertyManager, UpdateCallback
from .property_repository import InMemoryPropertyRepository, PropertyRepository
from .socket_reader import PropertyUpdateMessage, PropertyUpdateStreamReader

__all__ = [
    "PropertyManager",
    "UpdateCallback",
    "PropertyRepository",
    "InMemoryPropertyRepository",
    "PropertyUpdateMessage",
    "PropertyUpdateStreamReader",
]
