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
import java.util.*
import javax.servlet.http.HttpServletResponse


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
        note.dateCreated = Utilities.getCurrentDateString()
        note.dateModified = Utilities.getCurrentDateString()
        note.dateSync = Utilities.getCurrentDateString()
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
                model: MutableMap<String, Any>,
                @RequestParam("username") username: String?,
                @RequestParam("password") password: String?): String {

        var user: User? = null

        if(username != null && userRepository?.existsByUsername(username)!!) {
            user = userRepository?.findByUsername(username)
        }

        if(user == null) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            return "User does not exist. Fix brain!";
        }

        val passwordEncoder = BCryptPasswordEncoder()

        if (!passwordEncoder.matches(password, user?.passwordHash)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return "Password incorrect. Fix brain!";
        }

        val accessToken = AccessToken()
        accessToken.userId = user!!.id!!
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
                model: MutableMap<String, Any>,
                @RequestParam("username") username: String?,
                @RequestParam("password") password: String?): String {

        var user: User? = null

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
        user.username = username!!
        user.passwordHash = passwordEncoder.encode(password)
        user.isActive = true
        userRepository?.save(user)

        val accessToken = AccessToken()
        accessToken.userId = user!!.id!!
        val createdToken = UUID.randomUUID().toString()
        accessToken.token = createdToken
        accessToken.valid = true
        accessTokenRepository?.save(accessToken)

        response.status = HttpServletResponse.SC_OK
        return createdToken
    }

    @CrossOrigin
    @RequestMapping("/api/getNotes")
    @ResponseBody
    fun getNotes(response: HttpServletResponse,
                 model: MutableMap<String, Any>,
                 @RequestParam("token") token: String?,
                 @RequestParam("lastSync") dateLastSync: String?): String {

        response.status = HttpServletResponse.SC_BAD_REQUEST

        if (token == null || token.length < 16 || dateLastSync == null) {
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

        val notes = noteRepository?.findAllByUserIdSinceDate(accessToken.userId, dateLastSync!!);

        response.status = HttpServletResponse.SC_OK
        val json = ObjectMapper().registerModule(KotlinModule())
        return json.writeValueAsString(notes)
    }

    @CrossOrigin
    @RequestMapping("/api/setNotes")
    @ResponseBody
    fun setNotes(response: HttpServletResponse,
                 model: MutableMap<String, Any>,
                 @RequestParam("token") token: String?,
                 @RequestBody jsonData: String?): String {

        response.status = HttpServletResponse.SC_BAD_REQUEST

        if (token == null || token.length < 16 || jsonData == null) {
            return "More parameters! Bad brain. Fix brain.";
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
        val list: List<Note> = json.readValue(jsonData)
        for(note in list) {
            note.dateSync = Utilities.getCurrentDateString()
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

        response.status = HttpServletResponse.SC_OK
        return json.writeValueAsString("OK")
    }

    // TODO delete notes by uuid

    @CrossOrigin
    @ResponseBody
    fun errorPage(model: MutableMap<String, Any>): String {
        return "error"
    }
}