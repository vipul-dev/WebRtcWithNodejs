const http = require('http')
const Socket = require('websocket').server
const server = http.createServer(() => { })
const port = 3000

server.listen(port, () => {
    console.log(`Server started on port ${port}`)

})


const websocket = new Socket({ httpServer: server })

const users = []

websocket.on('request', (req) => {
    const connection = req.accept()

    connection.on('message', (message) => {
        const data = JSON.parse(message.utf8Data)
        console.log(data)
        const user = findUser(data.name)

        switch (data.type) {
            case "store_user":
                if (user != null) {
                    // our user exists

                    connection.send(JSON.stringify({
                        type: 'user already exists'
                    }))
                    return
                }

                const newUser = { name: data.name, conn: connection }

                users.push(newUser)

                break

            case "start_call":
                let userToCall = findUser(data.target)
                if (userToCall) {
                    connection.send(JSON.stringify({
                        type: "call_response",
                        data: "user is ready for call"
                    }))
                } else {
                    connection.send(JSON.stringify({
                        type: "call_response",
                        data: "user is not online"
                    }))
                }

                break

            case "create_offer":
                let userToReceiveOffer = findUser(data.target)

                if (userToReceiveOffer) {
                    userToReceiveOffer.conn.send(JSON.stringify({
                        type: "offer_received",
                        name: data.name,
                        data: data.data.sdp
                    }))
                }

                break

            case "create_answer":
                let userToReceiveAnswer = findUser(data.target)

                if (userToReceiveAnswer) {
                    userToReceiveAnswer.conn.send(JSON.stringify({
                        type: "answer_received",
                        name: data.name,
                        data: data.data.sdp
                    }))
                }

                break

            case "ice_candidate":
                let userToReceiveIceCandidate = findUser(data.target)

                if (userToReceiveIceCandidate) {
                    userToReceiveIceCandidate.conn.send(JSON.stringify({
                        type: "ice_candidate",
                        name: data.name,
                        data: {
                            sdpMLineIndex: data.data.sdpMLineIndex,
                            sdpMid: data.data.sdpMid,
                            sdpCandidate: data.data.sdpCandidate
                        }
                    }))
                }

                break

                  case "end_call":
                                var userEndCall = findUser(data.target)
                                if (userEndCall) {
                                    userEndCall.conn.send(JSON.stringify({
                                        type: "end_call",
                                        name: data.name,
                                        target: data.target
                                    }))
                                }

                                break
        }

    })

    connection.on('close', () => {
        // console.log(connection)
        users.forEach(user => {
            if (user.conn === connection) {
                users.splice(users.indexOf(user), 1)
            }
        })
    })
})

const findUser = username => {
    for (let i = 0; i < users.length; i++) {
        if (users[i].name == username)
            return users[i]
    }
}

var peerConfiguration = {};
