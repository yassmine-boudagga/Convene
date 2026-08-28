// Input validation using Joi
const Joi = require('joi');

const schemas = {
  register: Joi.object({
    name: Joi.string()
      .min(2)
      .max(100)
      .required()
      .messages({
        'string.empty': 'Name is required',
        'string.min': 'Name must be at least 2 characters',
        'string.max': 'Name cannot exceed 100 characters'
      }),
    email: Joi.string()
      .email()
      .required()
      .messages({
        'string.empty': 'Email is required',
        'string.email': 'Please provide a valid email'
      }),
    password: Joi.string()
      .min(6)
      .required()
      .messages({
        'string.empty': 'Password is required',
        'string.min': 'Password must be at least 6 characters'
      })
  }),

  login: Joi.object({
    email: Joi.string()
      .email()
      .required()
      .messages({
        'string.empty': 'Email is required',
        'string.email': 'Please provide a valid email'
      }),
    password: Joi.string()
      .required()
      .messages({
        'string.empty': 'Password is required'
      })
  }),

  createMeeting: Joi.object({
    title: Joi.string()
      .min(3)
      .max(200)
      .required()
      .messages({
        'string.empty': 'Title is required',
        'string.min': 'Title must be at least 3 characters',
        'string.max': 'Title cannot exceed 200 characters'
      }),
    description: Joi.string()
      .max(2000)
      .allow('')
      .messages({
        'string.max': 'Description cannot exceed 2000 characters'
      }),
    startTime: Joi.date()
      .iso()
      .min('now')
      .required()
      .messages({
        'date.base': 'Invalid start time',
        'date.min': 'Start time must be in the future',
        'any.required': 'Start time is required'
      }),
    duration: Joi.number()
      .min(1)
      .max(480)
      .required()
      .messages({
        'number.min': 'Duration must be at least 1 minute',
        'number.max': 'Duration cannot exceed 480 minutes',
        'any.required': 'Duration is required'
      }),
    meetingType: Joi.string()
      .valid('online', 'physical')
      .required()
      .messages({
        'any.only': 'Meeting type must be online or physical',
        'any.required': 'Meeting type is required'
      }),
    location: Joi.string()
      .trim()
      .max(300)
      .allow(null, '')
      .when('meetingType', {
        is: 'physical',
        then: Joi.string().trim().min(2).max(300).required().messages({
          'string.empty': 'Location is required for physical meetings',
          'string.min': 'Location must be at least 2 characters',
          'any.required': 'Location is required for physical meetings'
        }),
        otherwise: Joi.string().trim().allow(null, '')
      }),
    participants: Joi.array()
      .items(Joi.string().email())
      .min(1)
      .required()
      .messages({
        'array.min': 'At least one participant is required',
        'any.required': 'Participants are required'
      })
  }),

  updateMeeting: Joi.object({
    title: Joi.string()
      .min(3)
      .max(200)
      .messages({
        'string.min': 'Title must be at least 3 characters',
        'string.max': 'Title cannot exceed 200 characters'
      }),
    description: Joi.string()
      .max(2000)
      .allow('')
      .messages({
        'string.max': 'Description cannot exceed 2000 characters'
      }),
    startTime: Joi.date()
      .iso()
      .min('now')
      .messages({
        'date.base': 'Invalid start time',
        'date.min': 'Start time must be in the future'
      }),
    duration: Joi.number()
      .min(1)
      .max(480)
      .messages({
        'number.min': 'Duration must be at least 1 minute',
        'number.max': 'Duration cannot exceed 480 minutes'
      }),
    meetingType: Joi.string()
      .valid('online', 'physical')
      .messages({
        'any.only': 'Meeting type must be online or physical'
      }),
    location: Joi.string()
      .trim()
      .max(300)
      .allow(null, '')
      .when('meetingType', {
        is: 'physical',
        then: Joi.string().trim().min(2).max(300).required().messages({
          'string.empty': 'Location is required for physical meetings',
          'string.min': 'Location must be at least 2 characters',
          'any.required': 'Location is required for physical meetings'
        }),
        otherwise: Joi.string().trim().allow(null, '')
      }),
    participants: Joi.array()
      .items(Joi.string().email())
      .min(1)
      .messages({
        'array.min': 'At least one participant is required'
      })
  }),

  addNote: Joi.object({
    content: Joi.string()
      .min(1)
      .max(5000)
      .required()
      .messages({
        'string.empty': 'Note content is required',
        'string.min': 'Note content cannot be empty',
        'string.max': 'Note cannot exceed 5000 characters'
      })
  }),
};

const isDevelopment = process.env.NODE_ENV !== 'production';

const errorLog = (...args) => {
  console.error(...args);
};

// Validate middleware factory
const validate = (schemaName) => {
  return (req, res, next) => {
    const schema = schemas[schemaName];
    
    if (!schema) {
      errorLog(`Validation schema '${schemaName}' not found`);
      return res.status(500).json({
        success: false,
        message: 'Internal validation error'
      });
    }

    const { error, value } = schema.validate(req.body, {
      abortEarly: false,
      stripUnknown: true
    });

    if (error) {
      const errors = error.details.map(detail => ({
        field: detail.path.join('.'),
        message: detail.message
      }));

      return res.status(400).json({
        success: false,
        message: 'Validation failed',
        errors
      });
    }

    // Replace req.body with validated and sanitized value
    req.body = value;
    if ((schemaName === 'createMeeting' || schemaName === 'updateMeeting') && req.body.meetingType === 'online') {
      req.body.location = null;
    }

    next();
  };
};
module.exports = {
  validate,
  schemas
};
