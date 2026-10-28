package com.tom7.openremote

import com.tom7.openremote.data.AppJson
import com.tom7.openremote.data.FormList
import com.tom7.openremote.data.MessagePage
import com.tom7.openremote.data.PermissionList
import com.tom7.openremote.data.SessionMessage
import com.tom7.openremote.data.SessionPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireDecodingTest {
    @Test
    fun decodesSessionPage() {
        val page = AppJson.decodeFromString(
            SessionPage.serializer(),
            """
            {
              "data": [
                {
                  "id": "ses_abc",
                  "projectID": "proj_1",
                  "agent": "build",
                  "model": {"id": "flint-40b-free", "providerID": "opencode", "variant": "default"},
                  "cost": 0.42,
                  "time": {"created": 1000, "updated": 2000, "viewed": 1500},
                  "title": "Fix the parser",
                  "location": {"directory": "E:\\work\\repo"},
                  "unknownField": true
                },
                {"id": "ses_def"}
              ],
              "cursor": {"previous": "p1", "next": "n1"}
            }
            """.trimIndent(),
        )

        assertEquals(2, page.data.size)
        assertEquals("Fix the parser", page.data[0].displayTitle)
        assertEquals("flint-40b-free", page.data[0].model?.id)
        assertEquals("E:\\work\\repo", page.data[0].directory)
        assertTrue(!page.data[0].isArchived)
        assertEquals("Untitled session", page.data[1].displayTitle)
        assertEquals("n1", page.cursor.next)
    }

    @Test
    fun decodesAssistantMessageWithToolParts() {
        val message = AppJson.decodeFromString(
            SessionMessage.serializer(),
            """
            {
              "id": "msg_1",
              "type": "assistant",
              "time": {"created": 10, "completed": 20},
              "content": [
                {"type": "reasoning", "text": "Looking at the file"},
                {"type": "text", "text": "Here is the change."},
                {
                  "type": "tool",
                  "id": "call_1",
                  "name": "edit",
                  "executed": true,
                  "state": {
                    "status": "completed",
                    "input": {"path": "a.ts"},
                    "content": [{"type": "text", "text": "ok"}],
                    "metadata": {"diff": "@@ -1 +1 @@"}
                  }
                }
              ],
              "cost": 0.01,
              "finish": "stop"
            }
            """.trimIndent(),
        )

        assertEquals("assistant", message.type)
        assertEquals(3, message.content.size)
        assertEquals("edit", message.content[2].name)
        assertEquals("completed", message.content[2].state?.status)
        assertEquals("@@ -1 +1 @@", message.content[2].state?.metadata?.get("diff")?.toString()?.trim('"'))
        assertNull(message.error)
    }

    @Test
    fun decodesShellMessageAndUserDisplayText() {
        val shell = AppJson.decodeFromString(
            SessionMessage.serializer(),
            """
            {
              "id": "msg_shell",
              "type": "shell",
              "command": "git status",
              "status": "exited",
              "exit": 0,
              "output": {"output": "clean", "cursor": 5, "size": 5, "truncated": false},
              "time": {"created": 1}
            }
            """.trimIndent(),
        )

        assertEquals("git status", shell.command)
        assertEquals("clean", shell.output?.output)

        val user = AppJson.decodeFromString(
            SessionMessage.serializer(),
            """
            {
              "id": "msg_user",
              "type": "user",
              "text": "original",
              "metadata": {"displayText": "shown"},
              "files": [{"data": "abc", "mime": "image/png", "source": {"type": "inline"}, "name": "shot.png"}]
            }
            """.trimIndent(),
        )

        assertEquals("shown", user.displayText)
        assertEquals("shot.png", user.files.single().name)
        assertEquals("inline", user.files.single().source.type)
    }

    @Test
    fun decodesMessagePageWithEmptyOptionalFields() {
        val page = AppJson.decodeFromString(
            MessagePage.serializer(),
            """{"data":[{"id":"msg_2","type":"assistant","content":[]}],"cursor":{}}""",
        )

        assertEquals(1, page.data.size)
        assertTrue(page.data[0].isEmptyAssistant)
        assertNull(page.cursor.next)
    }

    @Test
    fun decodesPermissionRequests() {
        val list = AppJson.decodeFromString(
            PermissionList.serializer(),
            """
            {
              "data": [
                {
                  "id": "per_1",
                  "sessionID": "ses_abc",
                  "action": "bash",
                  "resources": ["npm test"],
                  "save": ["npm *"],
                  "message": "Run npm test?"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("bash", list.data.single().action)
        assertEquals(listOf("npm test"), list.data.single().resources)
        assertEquals("Run npm test?", list.data.single().message)
    }

    @Test
    fun decodesConditionalForm() {
        val list = AppJson.decodeFromString(
            FormList.serializer(),
            """
            {
              "data": [
                {
                  "id": "frm_1",
                  "sessionID": "ses_abc",
                  "title": "Choose a provider",
                  "fields": [
                    {
                      "key": "mode",
                      "type": "string",
                      "title": "Mode",
                      "required": true,
                      "default": "quick",
                      "options": [
                        {"value": "quick", "label": "Quick"},
                        {"value": "custom", "label": "Custom"}
                      ]
                    },
                    {
                      "key": "endpoint",
                      "type": "string",
                      "title": "Endpoint",
                      "when": [{"key": "mode", "op": "eq", "value": "custom"}]
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val form = list.data.single()
        assertEquals("Choose a provider", form.title)
        assertEquals("quick", form.fields[0].default?.toString()?.trim('"'))
        assertEquals("eq", form.fields[1].conditions?.single()?.op)
        assertEquals("Mode", form.fields[0].label)
        assertEquals("Endpoint", form.fields[1].label)
    }
}
