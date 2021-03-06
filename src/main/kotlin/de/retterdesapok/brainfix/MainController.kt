package de.retterdesapok.brainfix.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import de.retterdesapok.brainfix.Utilities
import de.retterdesapok.brainfix.dbaccess.AccessTokenRepository
import de.retterdesapok.brainfix.dbaccess.NoteRepository
import de.retterdesapok.brainfix.dbaccess.UserRepository
import de.retterdesapok.brainfix.entities.AccessToken
import de.retterdesapok.brainfix.entities.Note
import de.retterdesapok.brainfix.entities.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import javax.servlet.http.HttpServletResponse
import java.util.*

@Controller
class MainController {

    @Autowired
    private val userRepository: UserRepository? = null
    @Autowired
    private val accessTokenRepository: AccessTokenRepository? = null
    @Autowired
    private val noteRepository: NoteRepository? = null

    @Configuration
    @EnableWebMvc
    open class WebConfig: WebMvcConfigurer {
        override fun addCorsMappings(registry: CorsRegistry) {
            registry.addMapping("/**")
        }
    }

    @CrossOrigin
    @RequestMapping(path = arrayOf("/api/createtestuser"))
    @ResponseBody
    fun createAnton(): String {
        var anton = User()
        val passwordEncoder = BCryptPasswordEncoder()
        anton.passwordHash = passwordEncoder.encode("test")
        anton.username = "test"
        anton.isActive = true
        anton = userRepository?.save(anton)!!

        var note = Note()
        note.content = "Testnotiz für #Anton"
        note.dateCreated = Utilities.getDateStringNow()
        note.dateModified = Utilities.getDateStringNow()
        note.dateSync = Utilities.getDateStringNow()
        note.encryptionType = 0
        note.userId = anton.id!!
        note.uuid = UUID.randomUUID().toString()
        noteRepository?.save(note)!!

        val allUsers = userRepository.findAll()

        val json = ObjectMapper().registerModule(KotlinModule())
        return json.writeValueAsString(allUsers)
    }

    @CrossOrigin
    @RequestMapping(value = ["/test"])
    @ResponseBody
    fun testPage(): String {
        return "Test"
    }

    @CrossOrigin
    @RequestMapping("/api/login")
    @ResponseBody
    fun doLogin(response: HttpServletResponse,
                @RequestBody data : Map<String, String>): String {


        var username = data["username"]
        var password = data["password"]

        var user: User? = null

        if(username != null && userRepository?.existsByUsername(username)!!) {
            user = userRepository.findByUsername(username)
        }

        response.status = HttpServletResponse.SC_BAD_REQUEST
        if(user == null) {
            return "User does not exist. Fix brain!";
        } else if (user.failedLogins > 3) {
            return "Too many failed logins. Check brain and come again."
        }


        val passwordEncoder = BCryptPasswordEncoder()

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            user.failedLogins += 1
            user.lastFailedLogin = Utilities.getDateStringNow()
            userRepository?.save(user)
            return "Password incorrect. Fix brain!"
        } else if (user.failedLogins > 0 && user.lastFailedLogin > Utilities.getDateStringYesterday()) {
            user.failedLogins = 0
            userRepository?.save(user)
        }

        val accessToken = AccessToken()
        accessToken.userId = user.id!!
        val createdToken = UUID.randomUUID().toString()
        accessToken.token = createdToken
        accessToken.valid = true
        accessTokenRepository?.save(accessToken)

        response.status = HttpServletResponse.SC_OK
        return createdToken
    }

    @CrossOrigin
    @RequestMapping("/api/register")
    @ResponseBody
    fun doRegister(response: HttpServletResponse,
                   @RequestBody data : Map<String, String>): String {

        var username = data["username"]
        var password = data["password"]
        var user: User?

        if(username == null || password == null ||password.length == 0) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            return "Registering without a user name or password is stupid. Fix brain!";
        }

        if(userRepository?.existsByUsername(username)!!) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            return "User already exists. Fix brain!";
        }

        val passwordEncoder = BCryptPasswordEncoder()

        user = User()
        user.username = username
        user.passwordHash = passwordEncoder.encode(password)
        user.isActive = true
        userRepository.save(user)

        val accessToken = AccessToken()
        accessToken.userId = user.id!!
        val createdToken = UUID.randomUUID().toString()
        accessToken.token = createdToken
        accessToken.valid = true
        accessTokenRepository?.save(accessToken)

        response.status = HttpServletResponse.SC_OK
        return createdToken
    }

    @CrossOrigin
    @RequestMapping("/api/notes")
    @ResponseBody
    fun syncNotes(response: HttpServletResponse,
                 @RequestHeader("token") token: String?,
                 @RequestBody data : Map<String, kotlin.Any>): String {

        var dateLastSync = data["lastSync"] as String
        var notesList = data["notes"] as List<Note>

        response.status = HttpServletResponse.SC_BAD_REQUEST

        if (token == null || token.length < 16) {
            return "No many parameter. Fix brain!";
        }

        var accessToken: AccessToken? = null
        if(accessTokenRepository?.existsByToken(token)!!) {
            accessToken = accessTokenRepository.findByToken(token)
        }

        if (accessToken == null || !accessToken.valid) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return "This no valid token. Fix brain!";
        }

        val json = ObjectMapper().registerModule(KotlinModule())
        for(note in notesList) {
            note.dateSync = Utilities.getDateStringNow()
            note.synchronized = true
            note.userId = accessToken.userId
            val noteExists = noteRepository?.existsByUuid(note.uuid!!)
            if(!noteExists!!) {
                note.id = null
                noteRepository?.save(note)
            } else {
                val existingNote = noteRepository!!.findByUuid(note.uuid!!)
                existingNote.content = note.content
                existingNote.dateModified = note.dateModified
                noteRepository.save(existingNote)
            }
        }

        val notes = noteRepository?.findAllByUserIdSinceDate(accessToken.userId, dateLastSync)

        response.status = HttpServletResponse.SC_OK
        return json.writeValueAsString(notes)
    }

    @CrossOrigin
    @ResponseBody
    fun errorPage(): String {
        return "error"
    }
}